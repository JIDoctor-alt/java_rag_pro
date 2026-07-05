package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.superagent.rag.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 恋爱知识库 RAG 服务。
 * 方式一：Spring AI + PGvector 本地向量库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoveKnowledgeService {

    private final DocumentIngestionService documentIngestionService;
    private final VectorStore vectorStore;
    private final LoveMasterProperties properties;
    private final LoveKnowledgeCatalogService catalogService;

    public int ingestResource(Resource resource, String title) {
        Map<String, Object> metadata = baseMetadata(title, null, null, null, null);
        metadata.put("filename", resource.getFilename());
        return documentIngestionService.ingest(resource, metadata);
    }

    public int ingestText(String title, String content) {
        Map<String, Object> metadata = baseMetadata(title, null, null, null, null);
        return documentIngestionService.ingestText(content, metadata);
    }

    public int ingestSection(MarkdownChapterParser.ParsedDocument doc, MarkdownChapterParser.Section section) {
        String chapterPath = doc.getTitle() + " > " + section.getTitle();
        Map<String, Object> metadata = baseMetadata(
                section.getTitle(),
                doc.getDocId(),
                doc.getDocType(),
                section.getIndex(),
                chapterPath
        );
        metadata.put("documentTitle", doc.getTitle());
        String content = section.getTitle() + "\n\n" + section.getContent();
        return documentIngestionService.ingestText(content, metadata);
    }

    public List<Document> retrieve(String query) {
        return retrieve(query, null, null);
    }

    public List<Document> retrieve(String query, String docId, String docType) {
        var rag = properties.getRag();
        StringBuilder filter = new StringBuilder("category == '").append(rag.getCategory()).append("'");
        if (StringUtils.hasText(docType)) {
            filter.append(" && docType == '").append(docType).append("'");
        }
        if (StringUtils.hasText(docId)) {
            filter.append(" && docId == '").append(docId).append("'");
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(rag.getTopK())
                .similarityThreshold(rag.getSimilarityThreshold())
                .filterExpression(filter.toString())
                .build();
        return vectorStore.similaritySearch(request);
    }

    public int bootstrapFromClasspath() throws IOException {
        catalogService.buildCatalog();
        int total = 0;
        for (MarkdownChapterParser.ParsedDocument doc : catalogService.getAllDocuments()) {
            for (MarkdownChapterParser.Section section : doc.getSections()) {
                total += ingestSection(doc, section);
                log.info("Bootstrapped section: {} > {}", doc.getTitle(), section.getTitle());
            }
        }
        return total;
    }

    public void reindexDocument(MarkdownChapterParser.ParsedDocument doc) {
        deleteFromIndex(doc.getDocId());
        int total = 0;
        for (MarkdownChapterParser.Section section : doc.getSections()) {
            total += ingestSection(doc, section);
        }
        log.info("Reindexed document {} ({} chunks)", doc.getDocId(), total);
    }

    public void deleteFromIndex(String docId) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        String filter = "category == '" + properties.getRag().getCategory()
                + "' && docId == '" + docId + "'";
        try {
            vectorStore.delete(filter);
            log.info("Removed vector index for docId={}", docId);
        } catch (Exception e) {
            log.warn("Failed to remove vector index for docId={}: {}", docId, e.getMessage());
        }
    }

    public Map<String, Object> stats() {
        List<Document> sample = retrieve("恋爱沟通");
        return Map.of(
                "category", properties.getRag().getCategory(),
                "source", properties.getRag().getSource(),
                "enabled", properties.getRag().isEnabled(),
                "sampleHits", sample.size(),
                "docTypes", List.of("article", "course", "case"),
                "documentCount", catalogService.getAllDocuments().size()
        );
    }

    private Map<String, Object> baseMetadata(
            String title,
            String docId,
            String docType,
            Integer sectionIndex,
            String chapterPath) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", properties.getRag().getCategory());
        metadata.put("title", title);
        metadata.put("source", properties.getRag().getSource());
        if (docId != null) {
            metadata.put("docId", docId);
        }
        if (docType != null) {
            metadata.put("docType", docType);
        }
        if (sectionIndex != null) {
            metadata.put("sectionIndex", sectionIndex);
        }
        if (chapterPath != null) {
            metadata.put("chapterPath", chapterPath);
        }
        return metadata;
    }
}
