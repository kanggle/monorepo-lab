package com.example.promotion.application.exception;

/**
 * Thrown when an {@code Idempotency-Key} cannot be honoured as a replay of the
 * request it was first bound to (TASK-BE-536). Surfaces as HTTP 409
 * {@code IDEMPOTENCY_KEY_CONFLICT}. Two trigger paths, both conflicts:
 *
 * <ol>
 *   <li><b>Same key, different user batch.</b> The key is bound to the first
 *       request's {@code userIds}; replaying it for a different batch must not
 *       silently return the first batch's result.</li>
 *   <li><b>Lost the concurrent insert race.</b> Two simultaneous duplicates both
 *       miss the replay lookup; the {@code UNIQUE (promotion_id, idempotency_key)}
 *       constraint lets only one commit and the loser lands here. No coupons were
 *       minted on the losing request.</li>
 * </ol>
 *
 * <p>A same-key + same-batch replay is deliberately NOT routed here — that is the
 * normal retry, answered with the already-issued count and no re-issuance. Mirrors
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
