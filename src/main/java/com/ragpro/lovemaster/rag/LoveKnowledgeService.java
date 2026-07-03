package com.ragpro.lovemaster.rag;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.superagent.rag.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

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

    public int ingestResource(Resource resource, String title) {
        Map<String, Object> metadata = baseMetadata(title, resource);
        metadata.put("filename", resource.getFilename());
        return documentIngestionService.ingest(resource, metadata);
    }

    public int ingestText(String title, String content) {
        Map<String, Object> metadata = baseMetadata(title, null);
        return documentIngestionService.ingestText(content, metadata);
    }

    public List<Document> retrieve(String query) {
        var rag = properties.getRag();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(rag.getTopK())
                .similarityThreshold(rag.getSimilarityThreshold())
                .filterExpression("category == '" + rag.getCategory() + "'")
                .build();
        return vectorStore.similaritySearch(request);
    }

    public int bootstrapFromClasspath() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/love/**");
        int total = 0;
        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getFilename() == null) {
                continue;
            }
            String title = resource.getFilename();
            total += ingestResource(resource, title);
            log.info("Bootstrapped love knowledge: {}", title);
        }
        return total;
    }

    public Map<String, Object> stats() {
        List<Document> sample = retrieve("恋爱沟通");
        return Map.of(
                "category", properties.getRag().getCategory(),
                "source", properties.getRag().getSource(),
                "enabled", properties.getRag().isEnabled(),
                "sampleHits", sample.size(),
                "docTypes", List.of("article", "course", "case")
        );
    }

    private Map<String, Object> baseMetadata(String title, Resource resource) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", properties.getRag().getCategory());
        metadata.put("title", title);
        metadata.put("source", properties.getRag().getSource());
        if (resource != null) {
            enrichMetadataFromPath(metadata, resource);
        } else {
            metadata.put("docType", "article");
        }
        return metadata;
    }

    private void enrichMetadataFromPath(Map<String, Object> metadata, Resource resource) {
        String desc = resource.getDescription();
        String filename = resource.getFilename();
        String courseId = filename != null ? filename.replaceAll("\\.[^.]+$", "") : title(metadata);

        if (desc.contains("/courses/")) {
            metadata.put("docType", "course");
            metadata.put("courseId", courseId);
        } else if (desc.contains("/cases/")) {
            metadata.put("docType", "case");
        } else {
            metadata.put("docType", "article");
        }
    }

    private String title(Map<String, Object> metadata) {
        return String.valueOf(metadata.get("title"));
    }
}
