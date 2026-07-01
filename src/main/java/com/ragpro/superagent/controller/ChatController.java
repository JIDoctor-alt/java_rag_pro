package com.ragpro.superagent.controller;

import com.ragpro.superagent.agent.SuperAgentService;
import com.ragpro.superagent.model.ApiResult;
import com.ragpro.superagent.model.ChatRequest;
import com.ragpro.superagent.model.ChatResponse;
import com.ragpro.superagent.model.ModelInfo;
import com.ragpro.superagent.model.ModelTestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "智能体对话")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class ChatController {

    private final SuperAgentService superAgentService;

    @Operation(summary = "同步对话（RAG + Tools + Memory）")
    @PostMapping("/chat")
    public ApiResult<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String content = superAgentService.chat(
                request.getMessage(), request.getConversationId(), request.getModel());
        String model = request.getModel() != null ? request.getModel() : superAgentService.defaultModel();
        return ApiResult.ok(ChatResponse.builder()
                .content(content)
                .conversationId(request.getConversationId())
                .model(model)
                .build());
    }

    @Operation(summary = "流式对话（SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        return superAgentService.chatStream(
                request.getMessage(), request.getConversationId(), request.getModel());
    }

    @Operation(summary = "获取可用模型列表")
    @GetMapping("/models")
    public ApiResult<List<ModelInfo>> models() {
        return ApiResult.ok(superAgentService.listModels());
    }

    @Operation(summary = "获取当前默认模型")
    @GetMapping("/models/default")
    public ApiResult<Map<String, String>> defaultModel() {
        Map<String, String> result = new HashMap<>();
        result.put("modelId", superAgentService.defaultModel());
        return ApiResult.ok(result);
    }
}
