package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.LoveKnowledgeChapterDetail;
import com.ragpro.lovemaster.model.LoveKnowledgeChapterNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveKnowledgeCatalogService {

    private static final String GROUP_ARTICLE = "group-article";
    private static final String GROUP_COURSE = "group-course";
    private static final String GROUP_CASE = "group-case";

    private final LoveMasterProperties properties;

    @Getter
    private volatile List<LoveKnowledgeChapterNode> tree = List.of();

    private final Map<String, LoveKnowledgeChapterDetail> details = new ConcurrentHashMap<>();
    private final Map<String, MarkdownChapterParser.ParsedDocument> documents = new LinkedHashMap<>();
    private final Set<String> deletedDocIds = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        try {
            buildCatalog();
        } catch (IOException e) {
            log.warn("Knowledge catalog init failed: {}", e.getMessage());
        }
    }

    public synchronized void buildCatalog() throws IOException {
        documents.clear();
        details.clear();
        deletedDocIds.clear();
        loadDeletedMarkers();

        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/love/**");

        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getFilename() == null) {
                continue;
            }
            String filename = resource.getFilename();
            if (!filename.endsWith(".md")) {
                continue;
            }
            String relativePath = toRelativePath(resource.getDescription());
            String docId = filename.replaceAll("\\.[^.]+$", "");
            if (deletedDocIds.contains(docId)) {
                continue;
            }
            String docType = resolveDocType(relativePath);
            String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            putDocument(MarkdownChapterParser.parse(docId, docType, relativePath, raw));
        }

        loadDataDirOverlay();

        List<MarkdownChapterParser.ParsedDocument> docs = new ArrayList<>(documents.values());
        docs.sort(Comparator.comparing(MarkdownChapterParser.ParsedDocument::getTitle));
        tree = buildTree(docs);
        log.info("Knowledge catalog built: {} documents, {} detail entries", docs.size(), details.size());
    }

    public MarkdownChapterParser.ParsedDocument getDocument(String docId) {
        return documents.get(docId);
    }

    public synchronized void reloadDocument(MarkdownChapterParser.ParsedDocument updated) throws IOException {
        putDocument(updated);
        rebuildTree();
    }

    public synchronized void removeDocument(String docId) throws IOException {
        if (!documents.containsKey(docId)) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        MarkdownChapterParser.ParsedDocument doc = documents.get(docId);
        Path base = Paths.get(properties.getRag().getDataKnowledgeDir());
        Path deletedDir = base.resolve(".deleted");
        Files.createDirectories(deletedDir);
        Files.writeString(deletedDir.resolve(docId + ".marker"), "");
        deletedDocIds.add(docId);

        if (doc.getRelativePath() != null && !doc.getRelativePath().isBlank()) {
            Files.deleteIfExists(base.resolve(doc.getRelativePath()));
        }
        Files.deleteIfExists(base.resolve(docId + ".md"));

        documents.remove(docId);
        details.keySet().removeIf(key -> key.equals(docId) || key.startsWith(docId + "/"));
        rebuildTree();
        log.info("Removed knowledge document: {}", docId);
    }

    private void rebuildTree() {
        List<MarkdownChapterParser.ParsedDocument> docs = new ArrayList<>(documents.values());
        docs.sort(Comparator.comparing(MarkdownChapterParser.ParsedDocument::getTitle));
        tree = buildTree(docs);
    }

    private void loadDeletedMarkers() throws IOException {
        Path deletedDir = Paths.get(properties.getRag().getDataKnowledgeDir(), ".deleted");
        if (!Files.exists(deletedDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(deletedDir)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".marker"))
                    .map(name -> name.substring(0, name.length() - ".marker".length()))
                    .forEach(deletedDocIds::add);
        }
    }

    public List<MarkdownChapterParser.ParsedDocument> getAllDocuments() {
        return List.copyOf(documents.values());
    }

    public String findDocumentTitle(String docId) {
        MarkdownChapterParser.ParsedDocument doc = documents.get(docId);
        return doc != null ? doc.getTitle() : docId;
    }

    public LoveKnowledgeChapterDetail getChapter(String docId, Integer sectionIndex) {
        String key = detailKey(docId, sectionIndex);
        LoveKnowledgeChapterDetail cached = details.get(key);
        if (cached != null) {
            return cached;
        }
        MarkdownChapterParser.ParsedDocument doc = documents.get(docId);
        if (doc == null) {
            return null;
        }
        return buildDetail(doc, sectionIndex);
    }

    public String chapterPath(String docId, Integer sectionIndex) {
        MarkdownChapterParser.ParsedDocument doc = documents.get(docId);
        if (doc == null) {
            return "";
        }
        if (sectionIndex == null) {
            return doc.getTitle();
        }
        return doc.getSections().stream()
                .filter(s -> s.getIndex() == sectionIndex)
                .findFirst()
                .map(s -> doc.getTitle() + " > " + s.getTitle())
                .orElse(doc.getTitle());
    }

    private void loadDataDirOverlay() throws IOException {
        Path dataDir = Paths.get(properties.getRag().getDataKnowledgeDir());
        if (!Files.exists(dataDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dataDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains(".deleted"))
                    .forEach(this::loadDataFile);
        }
    }

    private void loadDataFile(Path file) {
        try {
            String relativePath = Paths.get(properties.getRag().getDataKnowledgeDir())
                    .relativize(file)
                    .toString()
                    .replace('\\', '/');
            String filename = file.getFileName().toString();
            String docId = filename.replaceAll("\\.[^.]+$", "");
            if (deletedDocIds.contains(docId)) {
                return;
            }
            String docType = resolveDocType(relativePath);
            String raw = Files.readString(file);
            putDocument(MarkdownChapterParser.parse(docId, docType, relativePath, raw));
            log.debug("Loaded data knowledge file: {}", relativePath);
        } catch (IOException e) {
            log.warn("Failed to load data knowledge file {}: {}", file, e.getMessage());
        }
    }

    private void putDocument(MarkdownChapterParser.ParsedDocument doc) {
        documents.put(doc.getDocId(), doc);
        details.keySet().removeIf(key -> key.equals(doc.getDocId()) || key.startsWith(doc.getDocId() + "/"));
        cacheDetail(doc, null);
        for (MarkdownChapterParser.Section section : doc.getSections()) {
            if (doc.getSections().size() == 1 && section.getTitle().equals(doc.getTitle())) {
                continue;
            }
            cacheDetail(doc, section.getIndex());
        }
    }

    private List<LoveKnowledgeChapterNode> buildTree(List<MarkdownChapterParser.ParsedDocument> docs) {
        var articleGroup = groupNode(GROUP_ARTICLE, "文章知识", "group", 0);
        var courseGroup = groupNode(GROUP_COURSE, "精选课程", "group", 1);
        var caseGroup = groupNode(GROUP_CASE, "成功案例", "group", 2);

        int articleOrder = 0;
        int courseOrder = 0;
        int caseOrder = 0;

        for (MarkdownChapterParser.ParsedDocument doc : docs) {
            LoveKnowledgeChapterNode parent = switch (doc.getDocType()) {
                case "course" -> courseGroup;
                case "case" -> caseGroup;
                default -> articleGroup;
            };
            int order = switch (doc.getDocType()) {
                case "course" -> courseOrder++;
                case "case" -> caseOrder++;
                default -> articleOrder++;
            };

            LoveKnowledgeChapterNode docNode = LoveKnowledgeChapterNode.builder()
                    .id(doc.getDocId())
                    .docId(doc.getDocId())
                    .title(doc.getTitle())
                    .docType(doc.getDocType())
                    .parentId(parent.getId())
                    .sortOrder(order)
                    .selectable(true)
                    .children(new ArrayList<>())
                    .build();
            parent.getChildren().add(docNode);

            int sectionOrder = 0;
            for (MarkdownChapterParser.Section section : doc.getSections()) {
                if (doc.getSections().size() == 1 && section.getTitle().equals(doc.getTitle())) {
                    continue;
                }
                LoveKnowledgeChapterNode sectionNode = LoveKnowledgeChapterNode.builder()
                        .id(doc.getDocId() + "/" + section.getIndex())
                        .docId(doc.getDocId())
                        .sectionIndex(section.getIndex())
                        .title(section.getTitle())
                        .docType(doc.getDocType())
                        .parentId(docNode.getId())
                        .sortOrder(sectionOrder++)
                        .selectable(true)
                        .build();
                docNode.getChildren().add(sectionNode);
            }
        }

        List<LoveKnowledgeChapterNode> roots = new ArrayList<>();
        if (!articleGroup.getChildren().isEmpty()) {
            roots.add(articleGroup);
        }
        if (!courseGroup.getChildren().isEmpty()) {
            roots.add(courseGroup);
        }
        if (!caseGroup.getChildren().isEmpty()) {
            roots.add(caseGroup);
        }
        return roots;
    }

    private LoveKnowledgeChapterNode groupNode(String id, String title, String docType, int sortOrder) {
        return LoveKnowledgeChapterNode.builder()
                .id(id)
                .title(title)
                .docType(docType)
                .sortOrder(sortOrder)
                .selectable(false)
                .children(new ArrayList<>())
                .build();
    }

    private void cacheDetail(MarkdownChapterParser.ParsedDocument doc, Integer sectionIndex) {
        LoveKnowledgeChapterDetail detail = buildDetail(doc, sectionIndex);
        if (detail != null) {
            details.put(detailKey(doc.getDocId(), sectionIndex), detail);
        }
    }

    private LoveKnowledgeChapterDetail buildDetail(MarkdownChapterParser.ParsedDocument doc, Integer sectionIndex) {
        String groupTitle = groupTitle(doc.getDocType());
        String groupId = groupId(doc.getDocType());

        List<LoveKnowledgeChapterDetail.Breadcrumb> breadcrumb = new ArrayList<>();
        breadcrumb.add(LoveKnowledgeChapterDetail.Breadcrumb.builder().id(groupId).title(groupTitle).build());
        breadcrumb.add(LoveKnowledgeChapterDetail.Breadcrumb.builder().id(doc.getDocId()).title(doc.getTitle()).build());

        String content;
        String title;
        String id;
        String chapterPath;

        if (sectionIndex == null) {
            id = doc.getDocId();
            title = doc.getTitle();
            content = doc.getFullContent();
            chapterPath = doc.getTitle();
        } else {
            MarkdownChapterParser.Section section = doc.getSections().stream()
                    .filter(s -> s.getIndex() == sectionIndex)
                    .findFirst()
                    .orElse(null);
            if (section == null) {
                return null;
            }
            id = doc.getDocId() + "/" + sectionIndex;
            title = section.getTitle();
            content = "## " + section.getTitle() + "\n\n" + section.getContent();
            chapterPath = doc.getTitle() + " > " + section.getTitle();
            breadcrumb.add(LoveKnowledgeChapterDetail.Breadcrumb.builder().id(id).title(title).build());
        }

        return LoveKnowledgeChapterDetail.builder()
                .id(id)
                .docId(doc.getDocId())
                .sectionIndex(sectionIndex)
                .title(title)
                .content(content)
                .docType(doc.getDocType())
                .chapterPath(chapterPath)
                .breadcrumb(breadcrumb)
                .build();
    }

    private String detailKey(String docId, Integer sectionIndex) {
        return sectionIndex == null ? docId : docId + "/" + sectionIndex;
    }

    private String toRelativePath(String description) {
        int idx = description.indexOf("knowledge/love/");
        if (idx >= 0) {
            return description.substring(idx + "knowledge/love/".length()).replace('\\', '/');
        }
        return description;
    }

    private String resolveDocType(String relativePath) {
        if (relativePath.contains("courses/")) {
            return "course";
        }
        if (relativePath.contains("cases/")) {
            return "case";
        }
        return "article";
    }

    private String groupTitle(String docType) {
        return switch (docType) {
            case "course" -> "精选课程";
            case "case" -> "成功案例";
            default -> "文章知识";
        };
    }

    private String groupId(String docType) {
        return switch (docType) {
            case "course" -> GROUP_COURSE;
            case "case" -> GROUP_CASE;
            default -> GROUP_ARTICLE;
        };
    }
}
