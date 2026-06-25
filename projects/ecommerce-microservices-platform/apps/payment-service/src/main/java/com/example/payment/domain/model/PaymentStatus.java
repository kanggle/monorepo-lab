package com.example.payment.domain.model;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    /**
     * Terminal: the payment was voided because its order was cancelled before capture
     * (TASK-BE-435). Distinct from {@code FAILED} (a PG-side rejection) — {@code VOIDED}
     * is a system-initiated, money-safe cancel of a never-captured PENDING payment, so a
     * later {@code confirm()} must be rejected rather than capturing funds. No PG money
     * movement ever occurred, so no refund is owed.
     */
    VOIDED
}
