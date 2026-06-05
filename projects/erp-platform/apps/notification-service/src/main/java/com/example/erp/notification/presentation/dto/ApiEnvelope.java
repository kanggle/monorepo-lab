package com.example.erp.notification.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Success envelope {@code { data, meta }} (notification-api.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    /** Single-resource envelope. */
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, baseMeta());
    }

    /** Paginated list envelope ({@code meta.page/size/totalElements}). */
    public static <T> ApiEnvelope<List<T>> ofList(List<T> data, int page, int size,
                                                  long totalElements) {
        Map<String, Object> meta = baseMeta();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        return new ApiEnvelope<>(data, meta);
    }

    private static Map<String, Object> baseMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timestamp", Instant.now().toString());
        return meta;
    }
}
