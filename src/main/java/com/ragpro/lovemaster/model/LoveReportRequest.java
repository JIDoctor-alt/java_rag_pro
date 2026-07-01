package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoveReportRequest {

    @NotBlank(message = "昵称不能为空")
    private String name;

    private String gender = "未说明";

    @NotBlank(message = "请描述当前感情状况")
    private String situation;

    private String partnerInfo = "未提供";

    private String conversationId = "default";
}
