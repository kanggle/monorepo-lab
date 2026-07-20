package com.example.payment.application.exception;

/**
 * Thrown when the HTTP partial-refund path is entered without an {@code Idempotency-Key}.
 * Surfaces as HTTP 400 {@code IDEMPOTENCY_KEY_REQUIRED} (TASK-BE-535).
 *
 * <p><b>Why refused rather than a fallback.</b> ADR-002 Decision-3 asks a money-mutating
 * endpoint to accept a client key and deterministically reject the duplicate. Without a
 * key there is nothing to reject on: a second identical partial refund is
 * indistinguishable from a legitimate one, so a keyless request can only be served
 * non-idempotently. order-service chose {@code required = false} for order placement, and
 * the ADR-002 D3 census classified that as OPTIONAL — a hole. Repeating it here would
 * reproduce the hole on a <em>funds-out</em> endpoint, where the failure mode is a double
 * payout rather than a duplicate order.
 *
 * <p>Making the header mandatory was verified non-breaking: payment-service is the only
 * in-repo caller of this endpoint (no console or storefront caller exists).
 *
 * <p>The event-driven full-refund path ({@code OrderCancelled} →
 * {@code refundPayment(orderId)}) is unaffected — it is idempotent on payment state
 * ({@code Payment.refund()} early-returns when already REFUNDED) and requires no key.
 */
public class IdempotencyKeyRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
