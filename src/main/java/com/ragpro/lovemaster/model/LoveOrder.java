package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LoveOrder {

    private Long id;
    private String courseId;
    private String courseName;
    private String outTradeNo;
    private String paymentProvider;
    private String payChannel;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private String transactionId;
    private String openId;
    private Long amountCents;
    private String currency;
    private String status;
    private String customerEmail;
    private Instant createdAt;
    private Instant updatedAt;
}
