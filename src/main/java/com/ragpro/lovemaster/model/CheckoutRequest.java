package com.ragpro.lovemaster.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "courseId 不能为空")
    private String courseId;

    private String customerEmail;
}
