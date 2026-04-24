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

    public void apply(String orderId, String requestUserId, Clock clock) {
        if (!this.userId.equals(requestUserId)) {
            throw new CouponNotOwnedException(this.couponId, requestUserId);
        }
        if (this.status == CouponStatus.USED) {
            throw new CouponAlreadyUsedException(this.couponId);
        }
        if (this.status == CouponStatus.EXPIRED || isExpired(clock)) {
            throw new CouponExpiredException(this.couponId);
        }
        this.status = CouponStatus.USED;
        this.usedAt = Instant.now(clock);
        this.orderId = orderId;
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
