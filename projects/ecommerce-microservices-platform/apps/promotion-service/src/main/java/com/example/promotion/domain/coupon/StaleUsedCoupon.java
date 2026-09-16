package com.example.promotion.domain.coupon;

import java.time.Instant;

/**
 * Read-only projection of a coupon that has been {@code USED} for a while, for batch-worker's
 * orphan-coupon reconciliation (TASK-INT-028).
 *
 * <p>Carries {@code tenantId} even though the {@link Coupon} aggregate is deliberately tenant-free:
 * the release that may follow is looked up tenant-scoped, so the caller must send this coupon's own
 * tenant or the release would silently find nothing.
 */
public record StaleUsedCoupon(String couponId, String orderId, String tenantId, Instant usedAt) {
}
