package com.example.payment.application.exception;

/**
 * Thrown when an {@code Idempotency-Key} cannot be honoured as a replay of the request it
 * was first bound to. Surfaces as HTTP 409 {@code IDEMPOTENCY_KEY_CONFLICT}
 * (TASK-BE-535). Two trigger paths, both conflicts:
 *
 * <ol>
 *   <li><b>Same key, different amount.</b> The key is bound to the first request's
 *       {@code amount}; replaying it for a different one must not silently return the
 *       first refund's result (Edge Case "Same key, different body"). 409 matches the
 *       repo precedent — order-service's {@code DUPLICATE_ORDER_REQUEST} and the shared
 *       library filter both 409 on a body mismatch.</li>
 *   <li><b>Lost the concurrent insert race.</b> Two simultaneous duplicates both miss the
 *       replay lookup; {@code UNIQUE (payment_id, idempotency_key)} lets only one commit
 *       and the loser lands here. No refund was performed on the losing request — its
 *       transaction rolls back before the PG call — and a retry finds the winner's record
 *       and replays normally. This mirrors {@code OrderPlacementService}'s
 *       {@code DataIntegrityViolationException} → conflict handling.</li>
 * </ol>
 *
 * <p>A same-key + same-amount replay is deliberately NOT routed here: that is the normal
 * retry, answered 200 with the payment's current state.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }

    public IdempotencyKeyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
