package com.siteguard.common.exception;


import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final Integer status;

    private final String code;

    private final String message;

    public AppException(String code, String message) {
        this(400, code, message);
    }

    public AppException(Integer status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public AppException(Integer status, String code, String message, Throwable e) {
        super(message, e);
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
