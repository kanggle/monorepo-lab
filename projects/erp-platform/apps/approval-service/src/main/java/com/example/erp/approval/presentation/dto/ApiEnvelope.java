package com.example.erp.approval.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Success envelope {@code { data, meta }} (approval-api.md). {@code meta} always
 * carries an ISO-8601 {@code timestamp}; list responses extend it with
 * {@code page} / {@code size} / {@code totalElements}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }

    public static <T> ApiEnvelope<List<T>> ofList(List<T> data, int page, int size) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", data.size());
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }
}
