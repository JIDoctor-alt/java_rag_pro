package com.ragpro.superagent.controller;

import com.ragpro.superagent.agent.SuperAgentService;
import com.ragpro.superagent.model.ApiResult;
import com.ragpro.superagent.model.ModelTestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "大模型管理")
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final SuperAgentService superAgentService;

    @Operation(summary = "测试大模型连通性", description = "发送探测消息验证 API Key 与模型是否可用")
    @PostMapping("/{modelId}/test")
    public ApiResult<ModelTestResult> testModel(@PathVariable String modelId) {
        return ApiResult.ok(superAgentService.testModel(modelId));
    }
}
