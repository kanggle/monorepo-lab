package com.example.admin.application.exception;

/**
 * TASK-BE-306 — generic "request body shape / structural validation failed"
 * exception used by the self-serve profile mutation endpoint (and any future
 * surface that needs the canonical {@code 400 INVALID_REQUEST} error code
 * defined in {@code admin-api.md § PATCH /api/admin/operators/me/profile}).
 *
 * <p>Mapped by {@code AdminExceptionHandler} to
 * {@code 400 {"code":"INVALID_REQUEST", ...}}.
 */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
