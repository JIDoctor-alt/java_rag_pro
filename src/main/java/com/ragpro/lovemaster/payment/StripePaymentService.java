package com.ragpro.lovemaster.payment;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.CheckoutResponse;
import com.ragpro.lovemaster.model.LoveOrder;
import com.ragpro.lovemaster.payment.domestic.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentService {

    private final LoveMasterProperties properties;
    private final LoveOrderRepository orderRepository;
    private final CourseProductService courseProductService;

    @PostConstruct
    public void init() {
        orderRepository.initSchema();
        if (courseProductService.hasCheckoutApi()) {
            Stripe.apiKey = properties.getPayment().getStripe().getSecretKey();
            log.info("Stripe Checkout API enabled");
        } else if (courseProductService.isPaymentEnabled()) {
            log.info("Stripe Payment Link mode enabled (no secret key required)");
        } else {
            log.info("Stripe payment disabled (set STRIPE_ENABLED=true)");
        }
    }

    public CheckoutResponse createCheckoutSession(String courseId, String customerEmail) throws StripeException {
        if (!courseProductService.isPaymentEnabled()) {
            throw new IllegalStateException("Stripe 支付未启用，请配置 STRIPE_ENABLED=true");
        }

        CourseProduct product = courseProductService.findProduct(courseId)
                .orElseThrow(() -> new IllegalArgumentException("未找到可购买课程: " + courseId));

        if (!courseProductService.isProductPurchasable(product)) {
            throw new IllegalArgumentException("该课程暂不可购买: " + courseId);
        }

        if (StringUtils.hasText(product.getPaymentLinkUrl())) {
            return CheckoutResponse.builder()
                    .sessionId("payment_link")
                    .checkoutUrl(product.getPaymentLinkUrl())
                    .courseId(product.getCourseId())
                    .courseName(product.getName())
                    .amountCents(product.getAmountCents())
                    .currency(product.getCurrency())
                    .build();
        }

        if (!courseProductService.hasCheckoutApi()) {
            throw new IllegalStateException("未配置 Payment Link 或 STRIPE_SECRET_KEY");
        }

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(properties.getPayment().getStripe().getSuccessUrl())
                .setCancelUrl(properties.getPayment().getStripe().getCancelUrl())
                .putMetadata("courseId", product.getCourseId())
                .putMetadata("courseName", product.getName())
                .addLineItem(buildLineItem(product));

        if (StringUtils.hasText(customerEmail)) {
            builder.setCustomerEmail(customerEmail);
        }

        Session session = Session.create(builder.build());
        Instant now = Instant.now();
        orderRepository.insert(LoveOrder.builder()
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .outTradeNo(session.getId())
                .paymentProvider(LoveOrderRepository.providerName(PaymentProvider.STRIPE))
                .stripeSessionId(session.getId())
                .amountCents(product.getAmountCents())
                .currency(product.getCurrency())
                .status("pending")
                .customerEmail(customerEmail)
                .createdAt(now)
                .updatedAt(now)
                .build());

        return CheckoutResponse.builder()
                .sessionId(session.getId())
                .checkoutUrl(session.getUrl())
                .courseId(product.getCourseId())
                .courseName(product.getName())
                .amountCents(product.getAmountCents())
                .currency(product.getCurrency())
                .build();
    }

    public Optional<LoveOrder> getOrderBySessionId(String sessionId) {
        Optional<LoveOrder> order = orderRepository.findBySessionId(sessionId);
        if (order.isEmpty() || !courseProductService.isPaymentEnabled()) {
            return order;
        }
        if (!"pending".equals(order.get().getStatus())) {
            return order;
        }
        try {
            Session session = Session.retrieve(sessionId);
            if ("paid".equals(session.getPaymentStatus())) {
                markOrderPaid(session);
                return orderRepository.findBySessionId(sessionId);
            }
        } catch (StripeException e) {
            log.warn("Failed to sync Stripe session {}: {}", sessionId, e.getMessage());
        }
        return order;
    }

    public void handleWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        String webhookSecret = properties.getPayment().getStripe().getWebhookSecret();
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("Stripe webhook secret not configured, skipping verification");
            return;
        }

        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .filter(obj -> obj instanceof Session)
                    .orElse(null);
            if (session == null) {
                log.warn("Unable to deserialize checkout.session.completed event");
                return;
            }
            markOrderPaid(session);
        }
    }

    private void markOrderPaid(Session session) {
        orderRepository.updateStripePaid(
                session.getId(),
                session.getPaymentIntent(),
                session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : null);
        log.info("Order paid: sessionId={}, courseId={}", session.getId(), session.getMetadata().get("courseId"));
    }

    private SessionCreateParams.LineItem buildLineItem(CourseProduct product) {
        SessionCreateParams.LineItem.Builder lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(1L);

        if (StringUtils.hasText(product.getStripePriceId())) {
            return lineItem.setPrice(product.getStripePriceId()).build();
        }

        return lineItem.setPriceData(
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(product.getCurrency())
                        .setUnitAmount(product.getAmountCents())
                        .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(product.getName())
                                        .putMetadata("courseId", product.getCourseId())
                                        .build())
                        .build())
                .build();
    }
}
