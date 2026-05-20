package com.example.erp.masterdata.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Success envelope {@code { data, meta }} (masterdata-api.md). {@code meta}
 * always carries an ISO-8601 {@code timestamp}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }

    public static <T> ApiEnvelope<T> of(T data, Map<String, Object> meta) {
        if (meta != null && !meta.containsKey("timestamp")) {
            meta.put("timestamp", Instant.now().toString());
        }
        return new ApiEnvelope<>(data, meta);
    }
}
