package com.example.promotion.infrastructure.persistence.entity;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponJpaEntity {

    @Id
    @Column(name = "coupon_id")
    private String couponId;

    @Column(name = "promotion_id", nullable = false)
    private String promotionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Column(name = "issued_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant issuedAt;

    @Column(name = "used_at", columnDefinition = "TIMESTAMP")
    private Instant usedAt;

    @Column(name = "expired_at", columnDefinition = "TIMESTAMP")
    private Instant expiredAt;

    @Column(name = "expires_at", columnDefinition = "TIMESTAMP")
    private Instant expiresAt;

    @Column(name = "order_id")
    private String orderId;

    public static CouponJpaEntity fromDomain(Coupon coupon) {
        CouponJpaEntity entity = new CouponJpaEntity();
        entity.couponId = coupon.getCouponId();
        entity.promotionId = coupon.getPromotionId();
        entity.userId = coupon.getUserId();
        entity.status = coupon.getStatus();
        entity.issuedAt = coupon.getIssuedAt();
        entity.usedAt = coupon.getUsedAt();
        entity.expiredAt = coupon.getExpiredAt();
        entity.expiresAt = coupon.getExpiresAt();
        entity.orderId = coupon.getOrderId();
        return entity;
    }

    public void updateFrom(Coupon coupon) {
        this.status = coupon.getStatus();
        this.usedAt = coupon.getUsedAt();
        this.expiredAt = coupon.getExpiredAt();
        this.orderId = coupon.getOrderId();
    }

    public Coupon toDomain() {
        return Coupon.reconstitute(
                couponId, promotionId, userId,
                status, issuedAt, usedAt,
                expiredAt, expiresAt, orderId
        );
    }
}
