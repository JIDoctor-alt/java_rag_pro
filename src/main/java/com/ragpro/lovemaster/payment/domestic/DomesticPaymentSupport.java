package com.ragpro.lovemaster.payment.domestic;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DomesticPaymentSupport {

    private final LoveMasterProperties properties;

    public boolean isDomesticEnabled() {
        return properties.getPayment().isEnabled()
                && properties.getPayment().getDomestic().isEnabled()
                && (!listChannels().isEmpty());
    }

    public boolean isWeChatConfigured() {
        var wx = properties.getPayment().getDomestic().getWechat();
        if (!wx.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(wx.getMchId())
                && StringUtils.hasText(wx.getApiV3Key())
                && StringUtils.hasText(wx.getPrivateKeyPath())
                && StringUtils.hasText(wx.getMerchantSerialNumber())
                && (StringUtils.hasText(wx.getMpAppId()) || StringUtils.hasText(wx.getAppAppId()));
    }

    public boolean isWeChatMockMode() {
        return properties.getPayment().getDomestic().getWechat().isEnabled() && !isWeChatConfigured();
    }

    public boolean isAlipayConfigured() {
        var ali = properties.getPayment().getDomestic().getAlipay();
        if (!ali.isEnabled()) {
            return false;
        }
        return StringUtils.hasText(ali.getAppId())
                && StringUtils.hasText(ali.getPrivateKey())
                && StringUtils.hasText(ali.getAlipayPublicKey());
    }

    public boolean isAlipayMockMode() {
        return properties.getPayment().getDomestic().getAlipay().isEnabled() && !isAlipayConfigured();
    }

    public List<Map<String, String>> listChannels() {
        List<Map<String, String>> channels = new ArrayList<>();
        var domestic = properties.getPayment().getDomestic();
        if (!domestic.isEnabled()) {
            return channels;
        }
        if (domestic.getWechat().isEnabled()) {
            if (isWeChatConfigured() || isWeChatMockMode()) {
                channels.add(channel(PayChannel.WECHAT_JSAPI, "微信公众号支付", "公众号内调起微信支付"));
                channels.add(channel(PayChannel.WECHAT_APP, "微信 App 支付", "原生 App 调起微信"));
            }
        }
        if (domestic.getAlipay().isEnabled()) {
            if (isAlipayConfigured() || isAlipayMockMode()) {
                channels.add(channel(PayChannel.ALIPAY_WAP, "支付宝 H5", "手机浏览器跳转支付宝"));
                channels.add(channel(PayChannel.ALIPAY_APP, "支付宝 App", "原生 App 调起支付宝"));
            }
        }
        return channels;
    }

    public String weChatNotifyUrl() {
        return properties.getPayment().getDomestic().getNotifyBaseUrl()
                + "/api/love-master/payment/wechat/notify";
    }

    public String alipayNotifyUrl() {
        return properties.getPayment().getDomestic().getNotifyBaseUrl()
                + "/api/love-master/payment/alipay/notify";
    }

    public String alipayGatewayUrl() {
        var ali = properties.getPayment().getDomestic().getAlipay();
        return ali.isSandbox() ? ali.getSandboxGatewayUrl() : ali.getGatewayUrl();
    }

    private Map<String, String> channel(PayChannel channel, String label, String description) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("channel", channel.name());
        item.put("label", label);
        item.put("description", description);
        item.put("mock", String.valueOf(
                (channel.name().startsWith("WECHAT") && isWeChatMockMode())
                        || (channel.name().startsWith("ALIPAY") && isAlipayMockMode())));
        return item;
    }
}
