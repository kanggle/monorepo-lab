package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.infrastructure.persistence.entity.CouponIssueRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@code coupon_issue_request} (TASK-BE-536). */
interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestJpaEntity, Long> {

    Optional<CouponIssueRequestJpaEntity> findByPromotionIdAndIdempotencyKey(
            String promotionId, String idempotencyKey);
}
