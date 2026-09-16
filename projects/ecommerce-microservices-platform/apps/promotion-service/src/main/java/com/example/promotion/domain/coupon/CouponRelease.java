package com.example.promotion.domain.coupon;

import java.time.Instant;

/**
 * A recorded release of {@code couponId} for {@code orderId} that had nothing to give back
 * (TASK-INT-027, Flyway V9 {@code coupon_release}).
 *
 * <p>order-service sends the release when a placement does not commit, but the release and the
 * {@code apply} it compensates are not ordered: the release is sent <em>because</em> the apply
 * timed out on the caller, which is exactly when that apply may still be executing here. When the
 * release arrives first the coupon is still {@code ISSUED} — there is nothing to revert — and
 * without this record the late apply would bind the coupon to an order that was never saved, where
 * nothing could free it again.
 *
 * <p>So the record is a <b>fence</b>, not an audit row: {@link CouponReleaseRepository#existsFor}
 * is what a later apply refuses on.
 *
 * <p>Scoped to the pair. The same coupon on a different {@code orderId} is untouched — a placement
 * that failed must not cost the user their coupon.
 *
 * <p>Immutable, and never mutated after recording. Kept indefinitely (no TTL), mirroring
 * {@link CouponIssueRequest}: a pruned row would silently re-open the window for any apply still
 * in flight.
 */
public final class CouponRelease {

    private final Long id;
    private final String couponId;
    private final String orderId;
    private final Instant createdAt;

    private CouponRelease(Long id, String couponId, String orderId, Instant createdAt) {
        this.id = id;
        this.couponId = couponId;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    /** A not-yet-persisted fence for an incoming release that found nothing to give back. */
    public static CouponRelease of(String couponId, String orderId, Instant createdAt) {
        return new CouponRelease(null, couponId, orderId, createdAt);
    }

    /** Rehydrates a persisted fence. */
    public static CouponRelease reconstitute(Long id, String couponId, String orderId, Instant createdAt) {
        return new CouponRelease(id, couponId, orderId, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getCouponId() {
        return couponId;
    }

    public String getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
