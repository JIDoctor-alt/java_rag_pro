package com.ragpro.lovemaster.controller;

import com.ragpro.lovemaster.model.DomesticPayRequest;
import com.ragpro.lovemaster.model.DomesticPayResponse;
import com.ragpro.lovemaster.model.LoveOrder;
import com.ragpro.lovemaster.payment.domestic.DomesticPaymentService;
import com.ragpro.lovemaster.payment.domestic.DomesticPaymentSupport;
import com.ragpro.lovemaster.payment.domestic.WeChatOAuthService;
import com.ragpro.lovemaster.payment.domestic.WeChatPayService;
import com.ragpro.lovemaster.payment.domestic.AlipayService;
import com.ragpro.superagent.model.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "AI 恋爱大师 - 国内支付")
@RestController
@RequestMapping("/api/love-master/payment")
@RequiredArgsConstructor
public class DomesticPaymentController {

    private final DomesticPaymentService domesticPaymentService;
    private final DomesticPaymentSupport domesticPaymentSupport;
    private final WeChatOAuthService weChatOAuthService;
    private final WeChatPayService weChatPayService;
    private final AlipayService alipayService;

    @Operation(summary = "国内支付渠道列表", description = "微信 JSAPI/App、支付宝 H5/App")
    @GetMapping("/domestic/channels")
    public ApiResult<List<Map<String, String>>> channels() {
        return ApiResult.ok(domesticPaymentSupport.listChannels());
    }

    @Operation(summary = "创建国内支付订单")
    @PostMapping("/domestic/create")
    public ApiResult<DomesticPayResponse> create(@Valid @RequestBody DomesticPayRequest request) {
        try {
            domesticPaymentService.validateChannel(request);
            return ApiResult.ok(domesticPaymentService.createPayment(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @Operation(summary = "模拟支付确认", description = "仅 mock 模式联调用")
    @PostMapping("/domestic/mock-paid/{outTradeNo}")
    public ApiResult<LoveOrder> mockPaid(@PathVariable String outTradeNo) {
        domesticPaymentService.confirmMockPaid(outTradeNo);
        return domesticPaymentService.findOrder(outTradeNo)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail("订单不存在"));
    }

    @Operation(summary = "按商户订单号查询")
    @GetMapping("/orders/trade/{outTradeNo}")
    public ApiResult<LoveOrder> orderByTradeNo(@PathVariable String outTradeNo) {
        return domesticPaymentService.findOrder(outTradeNo)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail("订单不存在"));
    }

    @Operation(summary = "微信公众号 OAuth 授权地址")
    @GetMapping("/wechat/oauth-url")
    public ApiResult<Map<String, String>> oauthUrl(@RequestParam(defaultValue = "love") String state) {
        try {
            return ApiResult.ok(Map.of("url", weChatOAuthService.buildOAuthUrl(state)));
        } catch (IllegalStateException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    @Operation(summary = "微信公众号 OAuth 回调", description = "微信服务器回调，完成后重定向回前端")
    @GetMapping("/wechat/oauth-callback")
    public ResponseEntity<Void> oauthCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state) {
        String openId = weChatOAuthService.exchangeOpenId(code);
        String redirect = weChatOAuthService.frontendRedirectWithOpenId(openId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, redirect).build();
    }

    @Operation(summary = "微信支付结果通知")
    @PostMapping(value = "/wechat/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> wechatNotify(
            @RequestBody String body,
            @RequestHeader HttpHeaders headers) {
        try {
            weChatPayService.handleNotify(body, headers);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.error("WeChat notify failed", e);
            return ResponseEntity.status(500).body(Map.of("code", "FAIL", "message", e.getMessage()));
        }
    }

    @Operation(summary = "支付宝支付结果通知")
    @PostMapping(value = "/alipay/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> alipayNotify(HttpServletRequest request) {
        try {
            Map<String, String> params = extractParams(request);
            alipayService.handleNotify(params);
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("Alipay notify failed", e);
            return ResponseEntity.ok("failure");
        }
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }
}
