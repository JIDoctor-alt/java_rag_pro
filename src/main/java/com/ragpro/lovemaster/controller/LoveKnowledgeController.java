package com.ragpro.lovemaster.controller;

import com.ragpro.lovemaster.advisor.LoveSensitiveAdvisor;
import com.ragpro.lovemaster.model.LoveKnowledgeAnswer;
import com.ragpro.lovemaster.model.LoveKnowledgeRequest;
import com.ragpro.lovemaster.model.LoveCourseInfo;
import com.ragpro.lovemaster.rag.LoveCourseRecommendService;
import com.ragpro.lovemaster.rag.LoveKnowledgeService;
import com.ragpro.lovemaster.service.LoveMasterService;
import com.ragpro.superagent.model.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "AI 恋爱大师 - RAG 知识库")
@RestController
@RequestMapping("/api/love-master/knowledge")
@RequiredArgsConstructor
public class LoveKnowledgeController {

    private final LoveKnowledgeService loveKnowledgeService;
    private final LoveMasterService loveMasterService;
    private final LoveCourseRecommendService courseRecommendService;

    @Operation(summary = "RAG 知识问答", description = "Retrieve → Augment → Generate")
    @PostMapping("/ask")
    public ApiResult<LoveKnowledgeAnswer> ask(@Valid @RequestBody LoveKnowledgeRequest request) {
        try {
            return ApiResult.ok(loveMasterService.askKnowledge(request));
        } catch (LoveSensitiveAdvisor.LoveSensitiveException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @Operation(summary = "检索知识片段", description = "仅执行 Retrieve 步骤，用于演示 RAG 检索")
    @GetMapping("/search")
    public ApiResult<List<Map<String, Object>>> search(@RequestParam String query) {
        List<Document> docs = loveKnowledgeService.retrieve(query);
        List<Map<String, Object>> results = docs.stream()
                .map(doc -> Map.<String, Object>of(
                        "content", doc.getText(),
                        "title", doc.getMetadata().getOrDefault("title", "unknown"),
                        "score", doc.getScore() != null ? doc.getScore() : 0.0
                ))
                .toList();
        return ApiResult.ok(results);
    }

    @Operation(summary = "导入恋爱知识文本")
    @PostMapping("/text")
    public ApiResult<Map<String, Object>> ingestText(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "用户导入");
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ApiResult.fail("content 不能为空");
        }
        int chunks = loveKnowledgeService.ingestText(title, content);
        return ApiResult.ok(Map.of("title", title, "chunks", chunks));
    }

    @Operation(summary = "上传恋爱知识文档")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {
        int chunks = loveKnowledgeService.ingestResource(file.getResource(), file.getOriginalFilename());
        return ApiResult.ok(Map.of("filename", file.getOriginalFilename(), "chunks", chunks));
    }

    @Operation(summary = "课程推荐", description = "基于 RAG 检索推荐相关课程/服务")
    @GetMapping("/courses/recommend")
    public ApiResult<List<LoveCourseInfo>> recommendCourses(@RequestParam String question) {
        return ApiResult.ok(courseRecommendService.recommend(question));
    }

    @Operation(summary = "知识库状态")
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(loveKnowledgeService.stats());
    }
}
