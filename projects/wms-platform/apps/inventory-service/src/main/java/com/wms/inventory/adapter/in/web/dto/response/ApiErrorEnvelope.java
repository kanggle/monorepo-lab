package com.wms.inventory.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Minimal error envelope returned by the gateway-error and access-denied
 * handlers in {@code SecurityConfig}. The full {@code GlobalExceptionHandler}
 * will use a richer body once REST endpoints come online in TASK-BE-022.
 *
 * <p>Shape compatible with {@code platform/error-handling.md}: {@code code},
 * {@code message}, and an ISO-8601 {@code timestamp}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(String code, String message, String timestamp) {

    public static ApiErrorEnvelope of(String code, String message) {
        return new ApiErrorEnvelope(code, message, Instant.now().toString());
    }
}
