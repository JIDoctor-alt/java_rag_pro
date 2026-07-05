package com.ragpro.lovemaster.payment.domestic;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.DomesticPayResponse;
import com.ragpro.lovemaster.payment.CourseProduct;
import com.ragpro.lovemaster.payment.LoveOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayService {

    private final LoveMasterProperties properties;
    private final DomesticPaymentSupport support;
    private final LoveOrderRepository orderRepository;

    public DomesticPayResponse createPayment(PayChannel channel, CourseProduct product, String outTradeNo) {
        return switch (channel) {
            case ALIPAY_WAP -> createWap(product, outTradeNo);
            case ALIPAY_APP -> createApp(product, outTradeNo);
            default -> throw new IllegalArgumentException("不支持的支付宝渠道: " + channel);
        };
    }

    public void handleNotify(Map<String, String> params) throws AlipayApiException {
        if (support.isAlipayMockMode()) {
            String outTradeNo = params.get("out_trade_no");
            if (outTradeNo != null) {
                orderRepository.updatePaid(outTradeNo, params.get("trade_no"), null);
            }
            return;
        }
        var ali = properties.getPayment().getDomestic().getAlipay();
        boolean verified = AlipaySignature.rsaCheckV1(
                params,
                ali.getAlipayPublicKey(),
                "UTF-8",
                "RSA2");
        if (!verified) {
            throw new IllegalArgumentException("支付宝回调验签失败");
        }
        if ("TRADE_SUCCESS".equals(params.get("trade_status"))
                || "TRADE_FINISHED".equals(params.get("trade_status"))) {
            orderRepository.updatePaid(params.get("out_trade_no"), params.get("trade_no"), null);
        }
    }

    private DomesticPayResponse createWap(CourseProduct product, String outTradeNo) {
        if (support.isAlipayMockMode()) {
            return mockWap(product, outTradeNo);
        }
        try {
            AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
            request.setNotifyUrl(support.alipayNotifyUrl());
            request.setReturnUrl(properties.getPayment().getDomestic().getAlipay().getReturnUrl());
            request.setBizContent(buildBizContent(product, outTradeNo, "QUICK_WAP_WAY"));
            String form = client().pageExecute(request).getBody();
            return DomesticPayResponse.builder()
                    .outTradeNo(outTradeNo)
                    .channel(PayChannel.ALIPAY_WAP.name())
                    .courseId(product.getCourseId())
                    .courseName(product.getName())
                    .amountCents(product.getAmountCents())
                    .mock(false)
                    .alipayForm(form)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("创建支付宝 H5 订单失败：" + e.getMessage(), e);
        }
    }

    private DomesticPayResponse createApp(CourseProduct product, String outTradeNo) {
        if (support.isAlipayMockMode()) {
            return mockApp(product, outTradeNo);
        }
        try {
            AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
            request.setNotifyUrl(support.alipayNotifyUrl());
            request.setBizContent(buildBizContent(product, outTradeNo, "QUICK_MSECURITY_PAY"));
            String orderString = client().sdkExecute(request).getBody();
            return DomesticPayResponse.builder()
                    .outTradeNo(outTradeNo)
                    .channel(PayChannel.ALIPAY_APP.name())
                    .courseId(product.getCourseId())
                    .courseName(product.getName())
                    .amountCents(product.getAmountCents())
                    .mock(false)
                    .alipayOrderString(orderString)
                    .build();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("创建支付宝 App 订单失败：" + e.getMessage(), e);
        }
    }

    private DomesticPayResponse mockWap(CourseProduct product, String outTradeNo) {
        String form = """
                <form id="alipay_mock_form" action="#" method="post">
                  <p>模拟支付宝 H5 支付：%s · %s元</p>
                  <button type="submit">模拟支付成功</button>
                </form>
                """.formatted(product.getName(), product.getAmountCents() / 100);
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.ALIPAY_WAP.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(true)
                .alipayForm(form)
                .build();
    }

    private DomesticPayResponse mockApp(CourseProduct product, String outTradeNo) {
        return DomesticPayResponse.builder()
                .outTradeNo(outTradeNo)
                .channel(PayChannel.ALIPAY_APP.name())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .mock(true)
                .alipayOrderString("mock_alipay_order_string")
                .build();
    }

    private String buildBizContent(CourseProduct product, String outTradeNo, String productCode) {
        BigDecimal amount = BigDecimal.valueOf(product.getAmountCents())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return """
                {
                  "out_trade_no":"%s",
                  "total_amount":"%s",
                  "subject":"%s",
                  "product_code":"%s"
                }
                """.formatted(outTradeNo, amount.toPlainString(), product.getName(), productCode);
    }

    private AlipayClient client() {
        var ali = properties.getPayment().getDomestic().getAlipay();
        return new DefaultAlipayClient(
                support.alipayGatewayUrl(),
                ali.getAppId(),
                ali.getPrivateKey(),
                "json",
                "UTF-8",
                ali.getAlipayPublicKey(),
                "RSA2");
    }
}
