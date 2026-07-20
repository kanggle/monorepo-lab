package com.example.product.domain.exception;

/**
 * Thrown when an {@code Idempotency-Key} cannot be honoured as a replay of the
 * request it was first bound to (TASK-BE-536). Surfaces as HTTP 409
 * {@code IDEMPOTENCY_KEY_CONFLICT}. Two trigger paths, both conflicts:
 *
 * <ol>
 *   <li><b>Same key, different payload.</b> The key is bound to the first request's
 *       defining field ({@code quantity} for stock adjustment, {@code name} for
 *       product create); replaying it with a different value must not silently
 *       return the first result.</li>
 *   <li><b>Lost the concurrent insert race.</b> Two simultaneous duplicates both miss
 *       the replay lookup; the {@code UNIQUE} constraint on the dedupe table lets only
 *       one commit and the loser lands here. No mutation was performed on the losing
 *       request.</li>
 * </ol>
 *
 * <p>A same-key + same-payload replay is deliberately NOT routed here — that is the
 * normal retry, answered with the current state and no re-mutation. Mirrors
 * payment-service's {@code IdempotencyKeyConflictException} (TASK-BE-535).
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }

    public IdempotencyKeyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
