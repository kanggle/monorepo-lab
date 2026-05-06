package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level success response envelope: {@code { data, meta }}.
 * meta always includes {@code timestamp} and {@code staleness} warning per S5.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static <T> ApiEnvelope<T> of(T data) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        // S5: explicit advisory that this data is eventually consistent
        meta.put("warning", "Not for procurement decisions (S5)");
        return new ApiEnvelope<>(data, meta);
    }

    public static <T> ApiEnvelope<T> of(T data, Map<String, Object> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        meta.put("warning", "Not for procurement decisions (S5)");
        if (extra != null) meta.putAll(extra);
        return new ApiEnvelope<>(data, meta);
    }
}
