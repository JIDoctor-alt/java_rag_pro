package com.ragpro.lovemaster.payment.domestic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.DomesticPayResponse;
import com.ragpro.lovemaster.payment.CourseProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatOAuthService {

    private final LoveMasterProperties properties;
    private final DomesticPaymentSupport support;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public String buildOAuthUrl(String state) {
        var wx = properties.getPayment().getDomestic().getWechat();
        String appId = wx.getMpAppId();
        if (!StringUtils.hasText(appId)) {
            throw new IllegalStateException("未配置 WECHAT_MP_APP_ID");
        }
        String redirectUri = URLEncoder.encode(
                properties.getPayment().getDomestic().getNotifyBaseUrl()
                        + "/api/love-master/payment/wechat/oauth-callback",
                StandardCharsets.UTF_8);
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + appId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=snsapi_base"
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "#wechat_redirect";
    }

    public String exchangeOpenId(String code) {
        if (support.isWeChatMockMode()) {
            return "mock_openid_" + code;
        }
        var wx = properties.getPayment().getDomestic().getWechat();
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token"
                + "?appid=" + wx.getMpAppId()
                + "&secret=" + wx.getMpAppSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";
        String body = restClient.get().uri(url).retrieve().body(String.class);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("openid")) {
                return node.get("openid").asText();
            }
            throw new IllegalStateException("获取 openId 失败：" + body);
        } catch (Exception e) {
            throw new IllegalStateException("解析 openId 失败：" + e.getMessage(), e);
        }
    }

    public String frontendRedirectWithOpenId(String openId) {
        String base = properties.getPayment().getDomestic().getWechat().getOauthFrontendRedirect();
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "openid=" + URLEncoder.encode(openId, StandardCharsets.UTF_8);
    }
}
