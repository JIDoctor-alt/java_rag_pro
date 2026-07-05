package com.ragpro.lovemaster.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CourseProduct {

    private String courseId;
    private String name;
    private String stripePriceId;
    private String paymentLinkUrl;
    private long amountCents;
    private String currency;
}
