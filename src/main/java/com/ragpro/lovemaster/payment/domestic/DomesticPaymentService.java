package com.ragpro.lovemaster.payment.domestic;

import com.ragpro.lovemaster.model.DomesticPayRequest;
import com.ragpro.lovemaster.model.DomesticPayResponse;
import com.ragpro.lovemaster.model.LoveOrder;
import com.ragpro.lovemaster.payment.CourseProduct;
import com.ragpro.lovemaster.payment.CourseProductService;
import com.ragpro.lovemaster.payment.LoveOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DomesticPaymentService {

    private final DomesticPaymentSupport support;
    private final CourseProductService courseProductService;
    private final LoveOrderRepository orderRepository;
    private final WeChatPayService weChatPayService;
    private final AlipayService alipayService;

    public DomesticPayResponse createPayment(DomesticPayRequest request) {
        if (!support.isDomesticEnabled()) {
            throw new IllegalStateException("国内支付未启用，请配置 love-master.payment.domestic");
        }
        PayChannel channel = PayChannel.valueOf(request.getChannel());
        CourseProduct product = courseProductService.findProduct(request.getCourseId())
                .filter(courseProductService::isProductPurchasable)
                .orElseThrow(() -> new IllegalArgumentException("未找到可购买课程: " + request.getCourseId()));

        String outTradeNo = orderRepository.nextOutTradeNo();
        Instant now = Instant.now();
        PaymentProvider provider = channel.name().startsWith("WECHAT")
                ? PaymentProvider.WECHAT : PaymentProvider.ALIPAY;

        orderRepository.insert(LoveOrder.builder()
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .outTradeNo(outTradeNo)
                .paymentProvider(LoveOrderRepository.providerName(provider))
                .payChannel(channel.name())
                .openId(request.getOpenId())
                .amountCents(product.getAmountCents())
                .currency("cny")
                .status("pending")
                .createdAt(now)
                .updatedAt(now)
                .build());

        DomesticPayResponse response = channel.name().startsWith("WECHAT")
                ? weChatPayService.createPayment(channel, product, outTradeNo, request.getOpenId())
                : alipayService.createPayment(channel, product, outTradeNo);
        return response;
    }

    public void confirmMockPaid(String outTradeNo) {
        orderRepository.updatePaid(outTradeNo, "mock_tx_" + outTradeNo, null);
    }

    public Optional<LoveOrder> findOrder(String outTradeNo) {
        return orderRepository.findByOutTradeNo(outTradeNo);
    }

    public void validateChannel(DomesticPayRequest request) {
        if (!StringUtils.hasText(request.getChannel())) {
            throw new IllegalArgumentException("channel 不能为空");
        }
        PayChannel channel = PayChannel.valueOf(request.getChannel());
        if (channel == PayChannel.WECHAT_JSAPI && !StringUtils.hasText(request.getOpenId())) {
            throw new IllegalArgumentException("微信公众号支付需要 openId");
        }
    }
}
