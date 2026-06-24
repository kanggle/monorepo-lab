package com.example.order.application.exception;

/**
 * Raised when a concurrent placement with an already-used {@code Idempotency-Key}
 * loses the (tenant, user, key) unique-index race (TASK-BE-430). The duplicate
 * order row was prevented; the client should retry, which resolves to the
 * winning order via the idempotent replay path. Mapped to 409 CONFLICT.
 */
public class DuplicateOrderPlacementException extends RuntimeException {

    public DuplicateOrderPlacementException(String idempotencyKey, Throwable cause) {
        super("Duplicate order placement for idempotency key: " + idempotencyKey, cause);
    }
}
