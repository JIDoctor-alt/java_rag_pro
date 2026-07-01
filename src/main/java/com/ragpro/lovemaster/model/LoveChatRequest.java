package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoveChatRequest {

    @NotBlank(message = "消息不能为空")
    private String message;

    private String conversationId = "default";

    /** 可选：图片 URL，用于多模态分析 */
    private String imageUrl;
}
