package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoveKnowledgeRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    private String conversationId = "default";

    /** 限定检索文档 ID（章节过滤） */
    private String docId;

    /** 限定检索文档类型：article / course / case */
    private String docType;
}
