package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.LoveKnowledgeChapterDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveKnowledgeChapterEditService {

    private final LoveMasterProperties properties;
    private final LoveKnowledgeCatalogService catalogService;
    private final LoveKnowledgeService knowledgeService;

    public LoveKnowledgeChapterDetail saveChapter(String docId, Integer sectionIndex, String content)
            throws IOException {
        MarkdownChapterParser.ParsedDocument doc = catalogService.getDocument(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        MarkdownChapterParser.ParsedDocument updated = sectionIndex == null
                ? MarkdownChapterParser.updateFullDocument(doc, content)
                : MarkdownChapterParser.updateSection(doc, sectionIndex, content);

        Path target = resolveDataPath(updated.getRelativePath(), docId);
        Files.createDirectories(target.getParent());
        Files.writeString(target, MarkdownChapterParser.toMarkdown(updated));
        log.info("Saved knowledge chapter: {}", target);

        catalogService.reloadDocument(updated);
        knowledgeService.reindexDocument(updated);

        LoveKnowledgeChapterDetail detail = catalogService.getChapter(docId, sectionIndex);
        if (detail == null) {
            throw new IllegalStateException("保存后读取章节失败");
        }
        return detail;
    }

    public void deleteChapter(String docId, Integer sectionIndex) throws IOException {
        if (sectionIndex == null) {
            catalogService.removeDocument(docId);
            knowledgeService.deleteFromIndex(docId);
            return;
        }

        MarkdownChapterParser.ParsedDocument doc = catalogService.getDocument(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        MarkdownChapterParser.ParsedDocument updated;
        try {
            updated = MarkdownChapterParser.deleteSection(doc, sectionIndex);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("最后一个章节")) {
                catalogService.removeDocument(docId);
                knowledgeService.deleteFromIndex(docId);
                return;
            }
            throw e;
        }

        Path target = resolveDataPath(updated.getRelativePath(), docId);
        Files.createDirectories(target.getParent());
        Files.writeString(target, MarkdownChapterParser.toMarkdown(updated));
        log.info("Deleted section {} from document {}", sectionIndex, docId);

        catalogService.reloadDocument(updated);
        knowledgeService.reindexDocument(updated);
    }

    private Path resolveDataPath(String relativePath, String docId) {
        Path base = Paths.get(properties.getRag().getDataKnowledgeDir());
        if (relativePath != null && !relativePath.isBlank()) {
            return base.resolve(relativePath);
        }
        return base.resolve(docId + ".md");
    }
}
