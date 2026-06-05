package com.example.erp.notification.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** Error envelope {@code { code, message, details?, timestamp }} (notification-api.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message,
                           Map<String, Object> details, Instant timestamp) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message, null, Instant.now());
    }
}
