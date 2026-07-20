package com.example.promotion.application.exception;

/**
 * Thrown when {@code POST /api/promotions/{promotionId}/coupons/issue} (TASK-BE-536)
 * is entered without a client {@code Idempotency-Key}. Surfaces as HTTP 400
 * {@code IDEMPOTENCY_KEY_REQUIRED}.
 *
 * <p>Refused rather than defaulted: {@code Promotion.validateCanIssue} only caps
 * the total issued count, not "this exact batch was already issued", so a keyless
 * replay can mint an entire second batch of coupons. Mirrors payment-service's
 * {@code IdempotencyKeyRequiredException} (TASK-BE-535).
 */
public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
