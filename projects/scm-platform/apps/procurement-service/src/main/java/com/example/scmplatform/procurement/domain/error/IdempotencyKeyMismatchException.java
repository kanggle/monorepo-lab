package com.example.scmplatform.procurement.domain.error;

/**
 * Raised when the same {@code Idempotency-Key} is reused with a different
 * payload (S2 Edge Case #8). Mapped to HTTP 422 {@code IDEMPOTENCY_KEY_MISMATCH}.
 */
public class IdempotencyKeyMismatchException extends RuntimeException {
    public IdempotencyKeyMismatchException(String message) {
        super(message);
    }
}
