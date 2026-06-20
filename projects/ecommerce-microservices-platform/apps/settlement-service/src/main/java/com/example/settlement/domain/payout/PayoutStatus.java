package com.example.settlement.domain.payout;

/**
 * Seller-payout lifecycle state (architecture.md § Period close + simulated payout).
 * A payout is created {@code PENDING} at period close; the execution transition
 * {@code PENDING → PAID | FAILED} runs the {@code SellerPayoutPort} simulated adapter
 * and is a <b>separate</b> operator step (TASK-BE-416 — NOT wired in this increment).
 */
public enum PayoutStatus {
    PENDING,
    PAID,
    FAILED
}
