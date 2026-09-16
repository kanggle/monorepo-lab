package com.example.promotion.domain.coupon;

/**
 * An {@code apply} arrived for a {@code (couponId, orderId)} whose release is already recorded
 * (TASK-INT-027) — the placement did not commit, so its order was never saved and the coupon must
 * not be bound to it. Maps to {@code 422 COUPON_PLACEMENT_RELEASED}.
 */
public class CouponPlacementReleasedException extends RuntimeException {

    public CouponPlacementReleasedException(String couponId, String orderId) {
        super("Coupon cannot be applied — the placement was already released: couponId=" + couponId
                + ", orderId=" + orderId);
    }
}
