package com.example.admin.application.exception;

/**
 * Thrown when a bulk-lock retry reuses a previously seen (operator, key) pair
 * with a different request payload. Surfaces as
 * {@code 409 IDEMPOTENCY_KEY_CONFLICT}.
 */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
