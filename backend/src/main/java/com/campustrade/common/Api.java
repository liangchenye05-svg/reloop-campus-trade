package com.campustrade.common;

import java.util.List;
import org.slf4j.MDC;

public record Api<T>(String code, String message, T data, String traceId) {
    public static <T> Api<T> ok(T data) {
        return new Api<>("OK", "成功", data, MDC.get("traceId"));
    }

    public static Api<Void> error(String code, String message) {
        return new Api<>(code, message, null, MDC.get("traceId"));
    }

    public record Page<T>(List<T> items, long total, int page, int size) {}
}
