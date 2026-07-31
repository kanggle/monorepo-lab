package com.example.erp.approval.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Error envelope {@code { code, message, details?, timestamp }} (approval-api.md).
 *
 * <p>ADR-MONO-058 § D2 (TASK-ERP-BE-038) — this type is <b>not</b> a duplicate of the
 * shared {@code com.example.web.dto.ErrorResponse}; it is the {@code details}-carrying
 * extension that {@code platform/error-handling.md § Error Response Format} explicitly
 * permits ("Services that return additional context … are permitted to extend this
 * envelope, but the three fields above must always be present"). Only the arms whose
 * contract documents {@code details} use it ({@code APPROVAL_ROUTE_INVALID}'s
 * {@code details.cause}, {@code APPROVAL_NOT_AUTHORIZED_APPROVER}'s {@code details.role});
 * every other arm in {@code presentation/advice/GlobalExceptionHandler} emits
 * {@code ErrorResponse}.
 *
 * <p>{@code timestamp} is a <b>pre-formatted ISO-8601 string</b>, not an {@code Instant}:
 * that makes a {@code details}-less {@code ApiErrorBody} byte-equal to
 * {@code ErrorResponse} <em>by construction</em> instead of depending on a
 * Boot-configured {@code ObjectMapper} disabling {@code WRITE_DATES_AS_TIMESTAMPS}
 * (fan-platform's {@code TASK-FAN-BE-038} found one service silently emitting a numeric
 * timestamp for exactly that reason). No observable change here — approval-service has
 * no {@code ObjectMapper} {@code @Bean} shadowing Boot's, so it already emitted the ISO
 * string.
 *
 * <p>There is deliberately no 2-argument factory: an {@code ApiErrorBody} with no
 * {@code details} is indistinguishable from {@code ErrorResponse}, and offering that
 * shortcut is how the type would silently grow back into a full duplicate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message,
                           Map<String, Object> details, String timestamp) {

    public static ApiErrorBody of(String code, String message, Map<String, Object> details) {
        return new ApiErrorBody(code, message, details, Instant.now().toString());
    }
}
