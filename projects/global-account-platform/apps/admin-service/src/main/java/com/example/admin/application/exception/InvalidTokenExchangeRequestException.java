package com.example.admin.application.exception;

/**
 * TASK-BE-298 / ADR-MONO-014 — the RFC 8693 token-exchange request was
 * malformed at the protocol level: {@code grant_type} or
 * {@code subject_token_type} did not equal the RFC 8693 fixed values.
 *
 * <p>Maps to {@code 400 BAD_REQUEST} (admin-api.md
 * {@code POST /api/admin/auth/token-exchange} error table) — a protocol
 * shape error, distinct from a {@code 401} subject-token / mapping failure.
 * Missing {@code subject_token} itself is bean-validation
 * ({@code 400 VALIDATION_ERROR}).
 */
public class InvalidTokenExchangeRequestException extends RuntimeException {
    public InvalidTokenExchangeRequestException(String message) {
        super(message);
    }
}
