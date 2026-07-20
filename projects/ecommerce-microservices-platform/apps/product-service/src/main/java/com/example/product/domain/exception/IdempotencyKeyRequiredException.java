package com.example.product.domain.exception;

/**
 * Thrown when a mutating endpoint that requires a client {@code Idempotency-Key}
 * (stock adjustment, product create — TASK-BE-536) is entered without one. Surfaces
 * as HTTP 400 {@code IDEMPOTENCY_KEY_REQUIRED}.
 *
 * <p>Refused rather than defaulted: both endpoints change a durable balance (stock
 * quantity / catalog ledger) where a genuine second request is byte-identical to a
 * retry of the first, so a keyless request can only be served non-idempotently
 * (ADR-002 Decision-3). Mirrors payment-service's {@code IdempotencyKeyRequiredException}
 * (TASK-BE-535).
 */
public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
