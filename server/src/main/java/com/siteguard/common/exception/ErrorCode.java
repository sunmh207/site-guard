package com.siteguard.common.exception;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.helpers.MessageFormatter;

@Data
@RequiredArgsConstructor
public class ErrorCode {

    private final Integer status;

    private final String code;

    private final String message;

    public static ErrorCode of(Integer status, String code, String message) {
        return new ErrorCode(status, code, message);
    }

    public AppException toException() {
        return new AppException(this.status, this.code, this.message);
    }

    public AppException toException(String message, Object... args) {
        var finalMessage = MessageFormatter.format(message, args).getMessage();
        return new AppException(this.status, this.code, finalMessage);
    }

    public AppException toException(Throwable e, String message, Object... args) {
        var finalMessage = MessageFormatter.format(message, args).getMessage();
        return new AppException(this.status, this.code, finalMessage, e);
    }
}
