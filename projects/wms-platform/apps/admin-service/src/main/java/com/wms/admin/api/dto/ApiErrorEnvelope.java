package com.wms.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Error envelope per {@code platform/error-handling.md}. Optional {@code details}
 * surfaces field-level VALIDATION_ERROR information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(Error error) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(new Error(code, message, Instant.now(), null, null, null));
    }

    public static ApiErrorEnvelope of(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope(new Error(code, message, Instant.now(), details, null, null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(
            String code,
            String message,
            Instant timestamp,
            Map<String, Object> details,
            String traceId,
            String requestId) {
    }
}
