package com.example.promotion.domain.coupon;

import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Getter
public class Coupon {

    private String couponId;
    private String promotionId;
    private String userId;
    private CouponStatus status;
    private Instant issuedAt;
    private Instant usedAt;
    private Instant expiredAt;
    private Instant expiresAt;
    private String orderId;

    private Coupon() {
    }

    public static Coupon issue(String promotionId, String userId, Instant expiresAt, Clock clock) {
        Coupon coupon = new Coupon();
        coupon.couponId = UUID.randomUUID().toString();
        coupon.promotionId = promotionId;
        coupon.userId = userId;
        coupon.status = CouponStatus.ISSUED;
        coupon.issuedAt = Instant.now(clock);
        coupon.expiresAt = expiresAt;
        return coupon;
    }

    public static Coupon reconstitute(String couponId, String promotionId, String userId,
                                       CouponStatus status, Instant issuedAt, Instant usedAt,
                                       Instant expiredAt, Instant expiresAt, String orderId) {
        Coupon coupon = new Coupon();
        coupon.couponId = couponId;
        coupon.promotionId = promotionId;
        coupon.userId = userId;
        coupon.status = status;
        coupon.issuedAt = issuedAt;
        coupon.usedAt = usedAt;
        coupon.expiredAt = expiredAt;
        coupon.expiresAt = expiresAt;
        coupon.orderId = orderId;
        return coupon;
    }

    /**
     * Uses the coupon for {@code orderId}.
     *
     * @return {@code true} when this call used the coupon; {@code false} when it was already used
     *         by the same order and the same user — an idempotent replay of the placement's apply
     *         (TASK-INT-026), in which nothing changes
     */
    public boolean apply(String orderId, String requestUserId, Clock clock) {
        if (!this.userId.equals(requestUserId)) {
            throw new CouponNotOwnedException(this.couponId, requestUserId);
        }
        if (this.status == CouponStatus.USED) {
            if (orderId != null && orderId.equals(this.orderId)) {
                return false;
            }
            throw new CouponAlreadyUsedException(this.couponId);
        }
        if (this.status == CouponStatus.EXPIRED || isExpired(clock)) {
            throw new CouponExpiredException(this.couponId);
        }
        this.status = CouponStatus.USED;
        this.usedAt = Instant.now(clock);
        this.orderId = orderId;
        return true;
    }

    /**
     * Gives the coupon back when the order placement that used it did not commit (TASK-INT-026).
     * Acts only if the coupon is used by {@code orderId} — a coupon used by another order, still
     * issued, or expired is left untouched.
     *
     * @return {@code true} if the coupon was reverted to issued
     */
    public boolean releaseFor(String orderId) {
        if (this.status != CouponStatus.USED || orderId == null || !orderId.equals(this.orderId)) {
            return false;
        }
        restore();
        return true;
    }

    public void restore() {
        if (this.status == CouponStatus.EXPIRED) {
            throw new CouponRestoreNotAllowedException(this.couponId);
        }
        if (this.status == CouponStatus.ISSUED) {
            return;
        }
        this.status = CouponStatus.ISSUED;
        this.usedAt = null;
        this.orderId = null;
    }

    public void expire(Clock clock) {
        if (this.status != CouponStatus.ISSUED) {
            return;
        }
        this.status = CouponStatus.EXPIRED;
        this.expiredAt = Instant.now(clock);
    }

    public boolean isExpired(Clock clock) {
        return expiresAt != null && Instant.now(clock).isAfter(expiresAt);
    }
}
