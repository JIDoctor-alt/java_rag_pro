package com.ragpro.lovemaster.controller;

import com.ragpro.lovemaster.advisor.LoveSensitiveAdvisor;
import com.ragpro.lovemaster.model.LoveChatRequest;
import com.ragpro.lovemaster.model.LoveReport;
import com.ragpro.lovemaster.model.LoveReportRequest;
import com.ragpro.lovemaster.service.LoveMasterService;
import com.ragpro.superagent.model.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Tag(name = "AI 恋爱大师")
@RestController
@RequestMapping("/api/love-master")
@RequiredArgsConstructor
public class LoveMasterController {

    private final LoveMasterService loveMasterService;

    @Operation(summary = "多轮对话", description = "ChatClient + Advisor + ChatMemory 持久化")
    @PostMapping("/chat")
    public ApiResult<String> chat(@Valid @RequestBody LoveChatRequest request) {
        try {
            String reply = loveMasterService.chat(
                    request.getMessage(), request.getConversationId(), request.getImageUrl());
            return ApiResult.ok(reply);
        } catch (LoveSensitiveAdvisor.LoveSensitiveException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @Operation(summary = "流式对话")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody LoveChatRequest request) {
        return loveMasterService.chatStream(request.getMessage(), request.getConversationId());
    }

    @Operation(summary = "恋爱报告", description = "Spring AI 结构化输出 + Prompt 模板")
    @PostMapping("/report")
    public ApiResult<LoveReport> report(@Valid @RequestBody LoveReportRequest request) {
        try {
            return ApiResult.ok(loveMasterService.generateReport(request));
        } catch (LoveSensitiveAdvisor.LoveSensitiveException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
