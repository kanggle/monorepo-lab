package com.example.order.domain.model;

/**
 * Why an order was cancelled (TASK-BE-435). Threaded from the cancel call through
 * {@link Order#cancel(CancelReason, java.time.Clock)} into the
 * {@code OrderCancelled} event payload so downstream consumers (payment-service
 * refund/void, promotion-service coupon restore) can branch on the cause.
 *
 * <ul>
 *   <li>{@link #OPERATOR} — a human-/user-initiated cancel (REST user cancel,
 *       operator internal API, backorder, user-withdrawal). The legacy default:
 *       a consumer reading an {@code OrderCancelled} event without a
 *       {@code cancelReason} field treats it as {@code OPERATOR} (back-compat).</li>
 *   <li>{@link #PAYMENT_TIMEOUT} — the stuck-detector's auto-resolving terminal
 *       (ADR-MONO-005 § 2.3 D3, refined by TASK-MONO-306): a payment-pending order
 *       that exhausted its grace attempts is auto-cancelled instead of landing in
 *       the terminal {@code STUCK_RECOVERY_FAILED}. Drives the money-safe
 *       refund/void compensation in payment-service.</li>
 * </ul>
 */
public enum CancelReason {
    OPERATOR,
    PAYMENT_TIMEOUT
}
