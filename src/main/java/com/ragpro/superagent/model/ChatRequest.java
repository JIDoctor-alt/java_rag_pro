package com.ragpro.superagent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private String conversationId = "default";

    private String model;
}
