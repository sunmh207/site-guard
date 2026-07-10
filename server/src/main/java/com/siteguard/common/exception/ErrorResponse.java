package com.siteguard.common.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final Long timestamp = System.currentTimeMillis();

    private final Integer status;

    private final String code;

    private final String message;

    public ErrorResponse(Integer status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public static ErrorResponse of(Integer status, String code, String message) {
        return new ErrorResponse(status, code, message);
    }

    public static ErrorResponse of(AppException ex) {
        return new ErrorResponse(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    public static ErrorResponse of(Exception ex) {
        return new ErrorResponse(500, "INTERNAL_ERROR", "服务器内部错误");
    }
}
