package com.example.erp.readmodel.adapter.inbound.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Success envelope {@code { data, meta }} (read-model-api.md). Every response's
 * {@code meta} carries an ISO-8601 {@code timestamp} and the read-model
 * {@code warning} so a consumer cannot mistake the projection for the
 * authoritative master (E5).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

    public static final String WARNING = "Eventually-consistent read-model";

    /** Single-resource envelope; optionally adds {@code meta.unresolved}. */
    public static <T> ApiEnvelope<T> of(T data, List<String> unresolved) {
        Map<String, Object> meta = baseMeta();
        if (unresolved != null && !unresolved.isEmpty()) {
            meta.put("unresolved", List.copyOf(unresolved));
        }
        return new ApiEnvelope<>(data, meta);
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
        meta.put("warning", WARNING);
        return meta;
    }
}
