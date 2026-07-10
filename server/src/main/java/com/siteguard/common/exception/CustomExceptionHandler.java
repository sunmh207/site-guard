package com.siteguard.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class CustomExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({AppException.class})
    public final ResponseEntity<ErrorResponse> handleAppExceptions(AppException ex, HttpServletRequest request) {
        var response = ErrorResponse.of(ex);

        if (Errors.INTERNAL_ERROR.getCode().equals(ex.getCode())) {
            log.error("服务器内部错误: uri={}, message={}", request.getRequestURI(), ex.getMessage(), ex);
        }

        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(response);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        String message = ex.getMessage();
        String code = determineErrorCode(ex);

        // 特殊处理参数校验异常，提取详细错误信息
        if (ex instanceof MethodArgumentNotValidException validEx) {
            message = validEx.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining("; "));
            code = "VALIDATION_ERROR";
        }

        log.warn("Spring MVC 异常: status={}, code={}, message={}", statusCode.value(), code, message);
        var response = ErrorResponse.of(statusCode.value(), code, message);
        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    /**
     * 处理所有其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: uri={}", request.getRequestURI(), e);
        var response = ErrorResponse.of(500, "INTERNAL_ERROR", "系统内部错误，请稍后重试");
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * 根据异常类型确定错误码
     */
    private String determineErrorCode(Exception ex) {
        String className = ex.getClass().getSimpleName();

        return switch (className) {
            case "HttpRequestMethodNotSupportedException" -> "METHOD_NOT_ALLOWED";
            case "HttpMediaTypeNotSupportedException" -> "UNSUPPORTED_MEDIA_TYPE";
            case "HttpMediaTypeNotAcceptableException" -> "NOT_ACCEPTABLE";
            case "MissingServletRequestParameterException" -> "MISSING_PARAMETER";
            case "TypeMismatchException", "MethodArgumentTypeMismatchException" -> "TYPE_MISMATCH";
            case "HttpMessageNotReadableException" -> "MALFORMED_REQUEST";
            case "MethodArgumentNotValidException" -> "VALIDATION_ERROR";
            case "BindException" -> "BINDING_ERROR";
            case "NoHandlerFoundException", "NoResourceFoundException" -> "NOT_FOUND";
            case "MissingServletRequestPartException" -> "MISSING_REQUEST_PART";
            case "AsyncRequestTimeoutException" -> "REQUEST_TIMEOUT";
            default -> "BAD_REQUEST";
        };
    }
}