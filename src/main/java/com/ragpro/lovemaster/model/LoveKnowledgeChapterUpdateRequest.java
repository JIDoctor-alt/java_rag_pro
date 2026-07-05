package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoveKnowledgeChapterUpdateRequest {

    @NotBlank(message = "docId 不能为空")
    private String docId;

    /** 小节序号，null 表示编辑整篇文档 */
    private Integer sectionIndex;

    @NotBlank(message = "content 不能为空")
    private String content;
}
