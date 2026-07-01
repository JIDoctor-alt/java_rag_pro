package com.ragpro.superagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        return ApiResult.<T>builder().code(0).message("success").data(data).build();
    }

    public static <T> ApiResult<T> fail(String message) {
        return ApiResult.<T>builder().code(-1).message(message).build();
    }
}
