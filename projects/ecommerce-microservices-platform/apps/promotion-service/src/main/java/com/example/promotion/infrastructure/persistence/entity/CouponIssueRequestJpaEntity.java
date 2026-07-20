package com.example.promotion.infrastructure.persistence.entity;

import com.example.promotion.domain.coupon.CouponIssueRequest;
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
 * JPA mapping for the {@code coupon_issue_request} table (TASK-BE-536, Flyway V8).
 */
@Entity
@Table(name = "coupon_issue_request",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coupon_issue_request_key",
                columnNames = {"promotion_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promotion_id", nullable = false)
    private String promotionId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "user_ids_digest", nullable = false, length = 64)
    private String userIdsDigest;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CouponIssueRequestJpaEntity fromDomain(CouponIssueRequest r) {
        CouponIssueRequestJpaEntity e = new CouponIssueRequestJpaEntity();
        e.id = r.getId();
        e.promotionId = r.getPromotionId();
        e.idempotencyKey = r.getIdempotencyKey();
        e.userIdsDigest = r.getUserIdsDigest();
        e.issuedCount = r.getIssuedCount();
        e.createdAt = r.getCreatedAt();
        return e;
    }

    public CouponIssueRequest toDomain() {
        return CouponIssueRequest.reconstitute(id, promotionId, idempotencyKey, userIdsDigest, issuedCount, createdAt);
    }
}
