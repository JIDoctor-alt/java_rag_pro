package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DomesticPayResponse {

    private String outTradeNo;
    private String channel;
    private String courseId;
    private String courseName;
    private Long amountCents;
    private boolean mock;

    private WeChatJsapiParams wechatJsapi;
    private WeChatAppParams wechatApp;
    private String alipayForm;
    private String alipayOrderString;

    @Data
    @Builder
    public static class WeChatJsapiParams {
        private String appId;
        private String timeStamp;
        private String nonceStr;
        private String packageValue;
        private String signType;
        private String paySign;
    }

    @Data
    @Builder
    public static class WeChatAppParams {
        private String appId;
        private String partnerId;
        private String prepayId;
        private String packageValue;
        private String nonceStr;
        private String timeStamp;
        private String sign;
    }
}
