package com.example.fanplatform.notification.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Platform error envelope: {@code { code, message, timestamp, details? }}.
 * {@code details} is omitted when absent ({@code @JsonInclude(NON_NULL)}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, String timestamp, Map<String, Object> details) {

    public static ApiErrorBody of(String code, String message) {
        return new ApiErrorBody(code, message, Instant.now().toString(), null);
    }

    public static ApiErrorBody of(String code, String message, Map<String, Object> details) {
        return new ApiErrorBody(code, message, Instant.now().toString(), details);
    }
}
