package com.example.fanplatform.notification.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level success response envelope: {@code { data, meta }}. Matches the
 * fan-platform {@code PageResponse} convention (same shape as membership /
 * community list endpoints).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }

    public static <T> ApiEnvelope<T> ofList(T data, int page, int size, long totalElements) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        return new ApiEnvelope<>(data, meta);
    }
}
