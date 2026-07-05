package com.ragpro.lovemaster.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutResponse {

    private String sessionId;
    private String checkoutUrl;
    private String courseId;
    private String courseName;
    private Long amountCents;
    private String currency;
}
