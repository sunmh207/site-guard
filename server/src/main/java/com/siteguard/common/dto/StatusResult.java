package com.siteguard.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.ConstraintViolation;
import lombok.Data;

import java.util.ArrayList;
import java.util.Set;

@Data
public class StatusResult<T> {

    private String code;

    private String message;

    private T data;

    @JsonIgnore
    public boolean isOk() {
        return "Ok".equalsIgnoreCase(code);
    }

    @JsonIgnore
    public boolean isFail() {
        return !isOk();
    }

    public static StatusResult<Void> ok() {
        var result = new StatusResult<Void>();
        result.setCode("Ok");
        return result;
    }

    public static <T> StatusResult<T> success(T data) {
        var result = new StatusResult<T>();
        result.setCode("Ok");
        result.setData(data);
        return result;
    }

    public static <T> StatusResult<T> error(String message) {
        var result = new StatusResult<T>();
        result.setCode("Failed");
        result.setMessage(message);
        return result;
    }

    public static <T> StatusResult<T> fail(String code, String message) {
        var result = new StatusResult<T>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> StatusResult<T> fail(String message) {
        var result = new StatusResult<T>();
        result.setCode("Failed");
        result.setMessage(message);
        return result;
    }

    public static <T> StatusResult<T> fail(Set<ConstraintViolation<T>> violations) {
        var messages = new ArrayList<String>();
        for(var v : violations) {
            messages.add(v.getMessage());
        }
        return fail("ValidateFailed", String.join("; ", messages));
    }
}