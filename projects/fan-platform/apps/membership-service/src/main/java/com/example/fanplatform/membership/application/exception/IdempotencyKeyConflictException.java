package com.example.fanplatform.membership.application.exception;

/**
 * Thrown when an {@code Idempotency-Key} is reused with a different payload
 * (fingerprint mismatch). Mapped to 409 {@code IDEMPOTENCY_KEY_CONFLICT}.
 */
public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency-Key reused with a different payload: " + idempotencyKey);
    }
}
