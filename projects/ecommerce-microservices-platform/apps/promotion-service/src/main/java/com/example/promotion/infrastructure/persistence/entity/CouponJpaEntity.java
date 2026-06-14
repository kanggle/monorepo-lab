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

    /**
     * Outer-axis tenant owning this coupon (ADR-MONO-030 Step 4, M1; TASK-BE-368).
     * Stamped once at issue from the request tenant context; immutable afterward, so
     * an apply/restore/expire update preserves the coupon's tenant — and the batch
     * expiry sweep reads it back to stamp the CouponExpired envelope (M5). Not part
     * of the clean {@code Coupon} domain model — persistence/event layers only.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

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

    public static CouponJpaEntity fromDomain(Coupon coupon, String tenantId) {
        CouponJpaEntity entity = new CouponJpaEntity();
        entity.couponId = coupon.getCouponId();
        entity.tenantId = tenantId;
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
