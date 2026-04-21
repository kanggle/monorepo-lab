package com.wms.master.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Error envelope matching
 * {@code specs/contracts/http/master-service-api.md §Error Envelope}.
 *
 * <p>Every {@link ApiError} carries a non-null {@code timestamp} (ISO 8601 UTC)
 * as required by {@code platform/error-handling.md § Error Response Format}.
 * The factory methods populate it with {@link Instant#now()} so no caller
 * needs to track the current time.
 */
public record ApiErrorEnvelope(ApiError error) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(new ApiError(code, message, Instant.now(), null, null, null));
    }

    public static ApiErrorEnvelope of(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope(new ApiError(code, message, Instant.now(), details, null, null));
    }

    public static ApiErrorEnvelope of(String code, String message, Map<String, Object> details,
                                      String traceId, String requestId) {
        return new ApiErrorEnvelope(new ApiError(code, message, Instant.now(), details, traceId, requestId));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiError(
            String code,
            String message,
            Instant timestamp,
            Map<String, Object> details,
            String traceId,
            String requestId) {

        /**
         * Compact canonical constructor — enforces the platform contract that
         * every error envelope carries a non-null {@code timestamp} (per
         * {@code platform/error-handling.md} § Error Response Format). A future
         * caller bypassing the {@link ApiErrorEnvelope} factory methods still
         * cannot construct an invalid record (TASK-BE-018 item 4).
         */
        public ApiError {
            Objects.requireNonNull(timestamp, "timestamp");
        }
    }
}
