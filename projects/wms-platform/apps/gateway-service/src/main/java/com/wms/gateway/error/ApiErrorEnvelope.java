package com.wms.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Gateway-level error envelope matching
 * {@code platform/error-handling.md} § Error Response Format. Uses the flat
 * shape (not the nested {@code {"error": {...}}} variant used by master-service
 * HTTP contract) because gateway errors are platform-level, not service-level.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(String code, String message, String timestamp) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(code, message, Instant.now().toString());
    }
}
