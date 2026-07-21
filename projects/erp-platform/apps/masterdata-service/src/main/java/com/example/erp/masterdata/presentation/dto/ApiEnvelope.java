package com.example.erp.masterdata.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Convenience factory for paginated list responses. Produces the standard
     * {@code { data, meta: { page, size, totalElements, timestamp } }} envelope
     * used by all five master-data list endpoints.
     *
     * <p>{@code totalElements} is the TRUE total-row count for the query (across
     * ALL pages), supplied by the repository's count query — NOT
     * {@code data.size()} (masterdata-api.md § PageMeta). A caller on page 0 of a
     * 25-row result sees {@code totalElements == 25} even though {@code data}
     * holds only the page slice.
     */
    public static <T> ApiEnvelope<List<T>> ofList(List<T> data, int page, int size, long totalElements) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }
}
