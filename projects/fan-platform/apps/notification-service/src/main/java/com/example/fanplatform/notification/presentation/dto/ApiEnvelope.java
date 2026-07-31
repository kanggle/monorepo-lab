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

    /**
     * @param totalPages total number of pages. Added by TASK-FAN-BE-043
     *                    (ADR-MONO-058 § D3 pagination-carrier adoption) — an
     *                    additive {@code meta} field; existing fields are
     *                    unchanged (backward-compatible per
     *                    {@code platform/error-handling.md}'s
     *                    permitted-to-extend precedent).
     */
    public static <T> ApiEnvelope<T> ofList(T data, int page, int size, long totalElements, int totalPages) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        meta.put("totalPages", totalPages);
        return new ApiEnvelope<>(data, meta);
    }
}
