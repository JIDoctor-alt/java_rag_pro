package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DomesticPayRequest {

    @NotBlank(message = "courseId 不能为空")
    private String courseId;

    /** WECHAT_JSAPI | WECHAT_APP | ALIPAY_WAP | ALIPAY_APP */
    @NotBlank(message = "channel 不能为空")
    private String channel;

    /** 公众号 JSAPI 必填：用户 openId */
    private String openId;
}
