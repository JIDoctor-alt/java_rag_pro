package com.ragpro.lovemaster.controller;

import com.ragpro.lovemaster.model.CheckoutRequest;
import com.ragpro.lovemaster.model.CheckoutResponse;
import com.ragpro.lovemaster.model.LoveCourseInfo;
import com.ragpro.lovemaster.model.LoveOrder;
import com.ragpro.lovemaster.payment.CourseProductService;
import com.ragpro.lovemaster.payment.StripePaymentService;
import com.ragpro.superagent.model.ApiResult;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "AI 恋爱大师 - 支付")
@RestController
@RequestMapping("/api/love-master/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final StripePaymentService stripePaymentService;
    private final CourseProductService courseProductService;

    @Operation(summary = "可购买课程列表")
    @GetMapping("/courses")
    public ApiResult<List<LoveCourseInfo>> courses() {
        return ApiResult.ok(courseProductService.listPurchasableCourses());
    }

    @Operation(summary = "创建 Stripe Checkout 会话")
    @PostMapping("/checkout")
    public ApiResult<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        try {
            return ApiResult.ok(stripePaymentService.createCheckoutSession(
                    request.getCourseId(), request.getCustomerEmail()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        } catch (StripeException e) {
            log.error("Stripe checkout failed", e);
            return ApiResult.fail("创建支付会话失败：" + e.getMessage());
        }
    }

    @Operation(summary = "查询订单状态")
    @GetMapping("/orders/{sessionId}")
    public ApiResult<LoveOrder> order(@PathVariable String sessionId) {
        return stripePaymentService.getOrderBySessionId(sessionId)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail("订单不存在"));
    }

    @Operation(summary = "Stripe Webhook", description = "接收 checkout.session.completed 等事件")
    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        try {
            stripePaymentService.handleWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("Stripe webhook error", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
