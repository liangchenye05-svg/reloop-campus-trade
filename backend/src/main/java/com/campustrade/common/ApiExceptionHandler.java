package com.campustrade.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Api<Void>> business(BusinessException exception) {
        return ResponseEntity.status(exception.status()).body(Api.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Api<Void>> invalidBody(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        String message = field == null ? "请求参数无效" : field.getField() + " " + field.getDefaultMessage();
        return ResponseEntity.badRequest().body(Api.error("INVALID_REQUEST", message));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Api<Void>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Api.error("INVALID_REQUEST", "请求参数无效"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Api<Void>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(Api.error("REQUEST_REJECTED", "无法处理此请求"));
        }
        log.error("Unhandled request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Api.error("INTERNAL_ERROR", "服务暂时不可用"));
    }
}
