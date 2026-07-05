package com.ragpro.lovemaster.payment.domestic;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.DomesticPayResponse;
import com.ragpro.lovemaster.payment.CourseProduct;
import com.ragpro.lovemaster.payment.LoveOrderRepository;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.app.model.Amount;
import com.wechat.pay.java.service.payments.app.model.PrepayRequest;
import com.wechat.pay.java.service.payments.app.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatPayService {

    private final LoveMasterProperties properties;
    private final DomesticPaymentSupport support;
    private final LoveOrderRepository orderRepository;

    private volatile Config config;

    public DomesticPayResponse createPayment(
            PayChannel channel, CourseProduct product, String outTradeNo, String openId) {
        return switch (channel) {
            case WECHAT_JSAPI -> createJsapi(product, outTradeNo, openId);
            case WECHAT_APP -> createApp(product, outTradeNo);
            default -> throw new IllegalArgumentException("不支持的微信支付渠道: " + channel);
        };
    }

    public void handleNotify(String body, HttpHeaders headers) {
        if (support.isWeChatMockMode()) {
            log.info("WeChat mock notify ignored");
            return;
        }
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(headers.getFirst("Wechatpay-Serial"))
                .nonce(headers.getFirst("Wechatpay-Nonce"))
                .signature(headers.getFirst("Wechatpay-Signature"))
                .timestamp(headers.getFirst("Wechatpay-Timestamp"))
                .body(body)
                .build();
        var wx = properties.getPayment().getDomestic().getWechat();
        NotificationParser parser = new NotificationParser((NotificationConfig) config());
        Transaction transaction = parser.parse(requestParam, Transaction.class);
        if (Transaction.TradeStateEnum.SUCCESS.equals(transaction.getTradeState())) {
            orderRepository.updatePaid(
                    transaction.getOutTradeNo(),
                    transaction.getTransactionId(),
                    transaction.getPayer() != null ? transaction.getPayer().getOpenid() : null);
        }
    }

    private DomesticPayResponse createJsapi(CourseProduct product, String outTradeNo, String openId) {
        if (!StringUtils.hasText(openId)) {
            throw new IllegalArgumentException("微信公众号支付需要 openId，请先完成微信 OAuth 授权");
        }
        if (support.isWeChatMockMode()) {
            return mockJsapi(product, outTradeNo);
        }
        var wx = properties.getPayment().getDomestic().getWechat();
        com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest request =
                new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        request.setAppid(wx.getMpAppId());
        request.setMchid(wx.getMchId());
        request.setDescription(product.getName());
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(support.weChatNotifyUrl());
        com.wechat.pay.java.service.payments.jsapi.model.Amount amount =
                new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(Math.toIntExact(product.getAmountCents()));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openId);
        request.setPayer(payer);

        JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(config()).build();
        var response = service.prepayWithRequestPayment(request);
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.WECHAT_JSAPI.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(false)
                .wechatJsapi(DomesticPayResponse.WeChatJsapiParams.builder()
                        .appId(response.getAppId())
                        .timeStamp(response.getTimeStamp())
                        .nonceStr(response.getNonceStr())
                        .packageValue(response.getPackageVal())
                        .signType(response.getSignType())
                        .paySign(response.getPaySign())
                        .build())
                .build();
    }

    private DomesticPayResponse createApp(CourseProduct product, String outTradeNo) {
        if (support.isWeChatMockMode()) {
            return mockApp(product, outTradeNo);
        }
        var wx = properties.getPayment().getDomestic().getWechat();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(wx.getAppAppId());
        request.setMchid(wx.getMchId());
        request.setDescription(product.getName());
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(support.weChatNotifyUrl());
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(product.getAmountCents()));
        amount.setCurrency("CNY");
        request.setAmount(amount);

        AppServiceExtension service = new AppServiceExtension.Builder().config(config()).build();
        PrepayWithRequestPaymentResponse response = service.prepayWithRequestPayment(request);
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.WECHAT_APP.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(false)
                .wechatApp(DomesticPayResponse.WeChatAppParams.builder()
                        .appId(response.getAppid())
                        .partnerId(response.getPartnerId())
                        .prepayId(response.getPrepayId())
                        .packageValue(response.getPackageVal())
                        .nonceStr(response.getNonceStr())
                        .timeStamp(response.getTimestamp())
                        .sign(response.getSign())
                        .build())
                .build();
    }

    private DomesticPayResponse mockJsapi(CourseProduct product, String outTradeNo) {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.WECHAT_JSAPI.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(true)
                .wechatJsapi(DomesticPayResponse.WeChatJsapiParams.builder()
                        .appId("mock_mp_app_id")
                        .timeStamp(ts)
                        .nonceStr("mock_nonce")
                        .packageValue("prepay_id=mock_prepay")
                        .signType("RSA")
                        .paySign("mock_sign")
                        .build())
                .build();
    }

    private DomesticPayResponse mockApp(CourseProduct product, String outTradeNo) {
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.WECHAT_APP.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(true)
                .wechatApp(DomesticPayResponse.WeChatAppParams.builder()
                        .appId("mock_app_id")
                        .partnerId("mock_mch")
                        .prepayId("mock_prepay")
                        .packageValue("Sign=WXPay")
                        .nonceStr("mock_nonce")
                        .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                        .sign("mock_sign")
                        .build())
                .build();
    }

    private Config config() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    var wx = properties.getPayment().getDomestic().getWechat();
                    config = new RSAAutoCertificateConfig.Builder()
                            .merchantId(wx.getMchId())
                            .privateKeyFromPath(wx.getPrivateKeyPath())
                            .merchantSerialNumber(wx.getMerchantSerialNumber())
                            .apiV3Key(wx.getApiV3Key())
                            .build();
                }
            }
        }
        return config;
    }
}
