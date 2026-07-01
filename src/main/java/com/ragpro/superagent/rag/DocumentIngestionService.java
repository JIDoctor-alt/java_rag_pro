package com.ragpro.superagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG 离线 ETL：Read → Split → Write
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    public int ingest(Resource resource, Map<String, Object> metadata) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        documents.forEach(doc -> doc.getMetadata().putAll(metadata));
        List<Document> chunks = textSplitter.apply(documents);
        vectorStore.add(chunks);
        log.info("Ingested {} chunks from {}", chunks.size(), resource.getFilename());
        return chunks.size();
    }

    public int ingestText(String content, Map<String, Object> metadata) {
        Document document = new Document(content, metadata);
        List<Document> chunks = textSplitter.apply(List.of(document));
        vectorStore.add(chunks);
        log.info("Ingested {} text chunks", chunks.size());
        return chunks.size();
    }
}
