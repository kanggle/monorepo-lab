package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global error envelope for console-bff inbound web controllers.
 *
 * <p>Error shape: {@code { "code": "...", "message": "...", "timestamp": "..." }}.
 * No stack traces in responses (platform/error-handling.md).
 *
 * <p><b>Scope intentionally limited to {@code adapter.inbound.web}.</b> Without
 * an explicit {@code basePackages}, {@code @RestControllerAdvice} applies to
 * every controller in the application context — including Spring Boot
 * Actuator endpoints (e.g. {@code /actuator/prometheus}). When an actuator
 * endpoint throws an exception (which it can for legitimate reasons during
 * metric registry scrape composition), this handler's wide
 * {@code @ExceptionHandler(Exception.class)} would swallow the throwable and
 * convert it to a generic {@code INTERNAL_ERROR} envelope — both
 * (a) breaking observability ({@code /actuator/prometheus} cannot return the
 * Prometheus exposition format) and (b) hiding the original stack trace from
 * diagnostics. Limiting to the inbound-web package lets actuator exceptions
 * propagate to Spring Boot's default error handling, which is correct for
 * those endpoints. (CI surface: PR #669 first three runs surfaced this as
 * "/actuator/prometheus 500 INTERNAL_SERVER_ERROR".)
 */
@RestControllerAdvice(basePackages = "com.kanggle.platformconsole.bff.adapter.inbound.web")
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingCredentialException.class)
    public ResponseEntity<ObjectNode> handleMissingCredential(MissingCredentialException ex) {
        return error(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ObjectNode> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> handleGeneric(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<ObjectNode> error(HttpStatus status, String code, String message) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("code", code);
        body.put("message", message != null ? message : "");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
