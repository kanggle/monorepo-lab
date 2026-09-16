package com.example.promotion.domain.coupon;

/**
 * Persistence port for release fences — the record that stops a late {@code apply} from binding a
 * coupon to a placement that was already released (TASK-INT-027, Flyway V9 {@code coupon_release}).
 *
 * <p>Both calls are made while the caller holds the coupon row's lock
 * ({@code findByIdForUpdate}), which is what serialises a release against a concurrent apply for
 * the same coupon. {@code UNIQUE (coupon_id, order_id)} stays as the backstop.
 */
public interface CouponReleaseRepository {

    /** Whether a release for this exact pair has already been recorded. */
    boolean existsFor(String couponId, String orderId);

    /** Records the fence. Callers check {@link #existsFor} first, under the coupon row's lock. */
    CouponRelease record(CouponRelease release);
}
