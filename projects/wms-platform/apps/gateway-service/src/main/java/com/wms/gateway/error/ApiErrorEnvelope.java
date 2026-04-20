package com.wms.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Gateway-level error envelope matching
 * {@code platform/error-handling.md} § Error Response Format. Uses the flat
 * shape (not the nested {@code {"error": {...}}} variant used by master-service
 * HTTP contract) because gateway errors are platform-level, not service-level.
 *
 * <p>The {@code timestamp} field is required by the platform envelope contract
 * and is populated by {@link #of(String, String)} with {@link Instant#now()}.
 * Jackson serializes {@link Instant} as an ISO 8601 UTC string when the
 * {@code JavaTimeModule} is registered (Spring Boot default).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(String code, String message, Instant timestamp) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(code, message, Instant.now());
    }
}
