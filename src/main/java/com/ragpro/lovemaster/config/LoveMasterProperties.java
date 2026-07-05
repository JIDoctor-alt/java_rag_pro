package com.ragpro.lovemaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "love-master")
public class LoveMasterProperties {

    private String systemPrompt;
    private int maxMemoryMessages = 30;
    private double temperature = 0.8;
    private RagProperties rag = new RagProperties();
    private PaymentProperties payment = new PaymentProperties();

    @Data
    public static class RagProperties {
        private boolean enabled = true;
        private String source = "local";
        private int topK = 5;
        private double similarityThreshold = 0.7;
        private String category = "love-master";
        private boolean bootstrapOnStartup = true;
        /** 可写知识库目录，编辑后的 Markdown 保存于此并覆盖 classpath 内容 */
        private String dataKnowledgeDir = "data/knowledge/love";
    }

    @Data
    public static class PaymentProperties {
        private boolean enabled = false;
        private StripeProperties stripe = new StripeProperties();
        private DomesticProperties domestic = new DomesticProperties();
        private Map<String, CourseProductProperties> courses = new LinkedHashMap<>();
    }

    @Data
    public static class DomesticProperties {
        private boolean enabled = false;
        /** 回调域名，如 https://api.example.com */
        private String notifyBaseUrl = "http://localhost:8080";
        private WeChatPayProperties wechat = new WeChatPayProperties();
        private AlipayProperties alipay = new AlipayProperties();
    }

    @Data
    public static class WeChatPayProperties {
        private boolean enabled = true;
        /** 公众号 AppID（JSAPI） */
        private String mpAppId = "";
        /** 公众号 AppSecret */
        private String mpAppSecret = "";
        /** 移动应用 AppID（APP 支付） */
        private String appAppId = "";
        private String mchId = "";
        private String apiV3Key = "";
        private String merchantSerialNumber = "";
        /** 商户 API 私钥 PEM 文件路径 */
        private String privateKeyPath = "";
        /** OAuth 完成后跳回前端的地址 */
        private String oauthFrontendRedirect = "http://localhost:5173/";
    }

    @Data
    public static class AlipayProperties {
        private boolean enabled = true;
        private String appId = "";
        private String privateKey = "";
        private String alipayPublicKey = "";
        private boolean sandbox = true;
        private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
        private String sandboxGatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
        private String returnUrl = "http://localhost:5173/?payment=success";
    }

    @Data
    public static class StripeProperties {
        private String secretKey = "";
        private String webhookSecret = "";
        private String successUrl = "http://localhost:5173/?payment=success&session_id={CHECKOUT_SESSION_ID}";
        private String cancelUrl = "http://localhost:5173/?payment=cancel";
        private String currency = "cny";
    }

    @Data
    public static class CourseProductProperties {
        /** Stripe Dashboard 中的 Price ID，留空则使用 amount-cents 动态创建 */
        private String stripePriceId = "";
        /** Stripe Payment Link（buy.stripe.com），配置后优先跳转，无需 API 创建 Session */
        private String paymentLinkUrl = "";
        private long amountCents;
        private String currency = "";
        private String name = "";
    }
}
