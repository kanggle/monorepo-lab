package com.example.fanplatform.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Gateway-level error envelope matching
 * {@code platform/error-handling.md} § Error Response Format. Uses the flat
 * shape (not the nested {@code {"error": {...}}} variant used by some service
 * HTTP contracts) because gateway errors are platform-level, not service-level.
 *
 * <p>The {@code timestamp} field is required by the platform envelope contract
 * and is populated by {@link #of(String, String)} with {@link Instant#now()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(String code, String message, Instant timestamp) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(code, message, Instant.now());
    }
}
