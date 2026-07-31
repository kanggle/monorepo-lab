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

    /**
     * Convenience factory for paginated list responses. Produces the standard
     * {@code { data, meta: { page, size, totalElements, timestamp } }} envelope
     * (approval-api.md § Common shapes → {@code PageMeta}).
     *
     * <p>{@code totalElements} is the TRUE total-row count for the query (across
     * ALL pages), supplied by the repository's count query — NOT
     * {@code data.size()} (mirrors masterdata-service /
     * notification-service / read-model-service's {@code ApiEnvelope.ofList}).
     * A caller on page 0 of a 25-row result sees {@code totalElements == 25}
     * even though {@code data} holds only the page slice.
     *
     * <p>Kept (no {@code totalPages}) for {@code DelegationController#list}, the
     * one approval-service list endpoint that is genuinely unpaginated (no
     * {@code page}/{@code size} query params in approval-api.md — the full grant
     * list is always returned) and therefore never adopts
     * {@code com.example.common.page.PageResult} (ADR-MONO-058 § D3 — ordinary
     * paginated endpoints use the {@link #ofList(List, int, int, long, int)}
     * overload below).
     */
    public static <T> ApiEnvelope<List<T>> ofList(List<T> data, int page, int size, long totalElements) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }

    /**
     * Paginated list envelope for endpoints backed by
     * {@code com.example.common.page.PageResult} — adds the additive
     * {@code totalPages} field (ADR-MONO-058 § D3).
     */
    public static <T> ApiEnvelope<List<T>> ofList(List<T> data, int page, int size,
                                                  long totalElements, int totalPages) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", totalElements);
        meta.put("totalPages", totalPages);
        meta.put("timestamp", Instant.now().toString());
        return new ApiEnvelope<>(data, meta);
    }
}
