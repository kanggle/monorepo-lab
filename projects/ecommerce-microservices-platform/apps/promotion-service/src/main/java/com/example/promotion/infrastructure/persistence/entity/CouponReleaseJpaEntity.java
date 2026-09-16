package com.example.promotion.infrastructure.persistence.entity;

import com.example.promotion.domain.coupon.CouponRelease;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA mapping for the {@code coupon_release} table (TASK-INT-027, Flyway V9).
 */
@Entity
@Table(name = "coupon_release",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coupon_release_pair",
                columnNames = {"coupon_id", "order_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponReleaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private String couponId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CouponReleaseJpaEntity fromDomain(CouponRelease r) {
        CouponReleaseJpaEntity e = new CouponReleaseJpaEntity();
        e.id = r.getId();
        e.couponId = r.getCouponId();
        e.orderId = r.getOrderId();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    public CouponRelease toDomain() {
        return CouponRelease.reconstitute(id, couponId, orderId, createdAt);
    }
}
