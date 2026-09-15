package com.example.order.application.port;

/**
 * Asks promotion-service for a coupon's discount at order placement, and gives the coupon
 * back when that placement does not commit (TASK-INT-026).
 *
 * <p>promotion-service is the authority on the discount amount — the client never sends it.
 */
public interface CouponDiscountPort {

    /**
     * Marks {@code couponId} used by {@code orderId} and returns the discount promotion-service
     * granted for {@code orderAmount} (the pre-discount line subtotal). Idempotent for the same
     * {@code orderId} on the promotion-service side.
     *
     * @throws com.example.order.application.exception.CouponRejectedException promotion-service refused the coupon
     * @throws com.example.order.application.exception.CouponServiceUnavailableException promotion-service did not answer
     */
    long applyCoupon(String couponId, String orderId, String userId, long orderAmount);

    /**
     * Best-effort: asks promotion-service to revert {@code couponId} to issued, which it does only
     * if the coupon is used by {@code orderId}. Never throws — a failure is logged.
     */
    void releaseCoupon(String couponId, String orderId);
}
