package com.example.promotion.application.command;

import java.util.List;

/**
 * {@code idempotencyKey} is required by {@code CouponCommandService.issueCoupons}
 * (TASK-BE-536) — a replayed request must not mint a second batch of coupons.
 */
public record IssueCouponsCommand(
        String promotionId,
        List<String> userIds,
        String userRole,
        String idempotencyKey
) {
    /** Backward-compat — {@code idempotencyKey} null triggers the service's own 400. */
    public IssueCouponsCommand(String promotionId, List<String> userIds, String userRole) {
        this(promotionId, userIds, userRole, null);
    }
}
