package com.ragpro.superagent.controller;

import com.ragpro.superagent.model.ApiResult;
import com.ragpro.superagent.rag.DocumentIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Tag(name = "RAG 知识库")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentIngestionService documentIngestionService;

    @Operation(summary = "上传文档到向量知识库")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) throws IOException {
        int chunks = documentIngestionService.ingest(
                file.getResource(),
                Map.of("filename", file.getOriginalFilename(), "category", category));
        return ApiResult.ok(Map.of(
                "filename", file.getOriginalFilename(),
                "chunks", chunks,
                "category", category
        ));
    }

    @Operation(summary = "导入纯文本到向量知识库")
    @PostMapping("/text")
    public ApiResult<Map<String, Object>> ingestText(
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        String category = body.getOrDefault("category", "general");
        if (content == null || content.isBlank()) {
            return ApiResult.fail("content 不能为空");
        }
        int chunks = documentIngestionService.ingestText(content, Map.of("category", category));
        return ApiResult.ok(Map.of("chunks", chunks, "category", category));
    }
}
