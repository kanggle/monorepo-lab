package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.domain.coupon.CouponIssueRequest;
import com.example.promotion.domain.coupon.CouponIssueRequestRepository;
import com.example.promotion.infrastructure.persistence.entity.CouponIssueRequestJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public Optional<CouponIssueRequest> find(String promotionId, String idempotencyKey) {
        return jpaRepository.findByPromotionIdAndIdempotencyKey(promotionId, idempotencyKey)
                .map(CouponIssueRequestJpaEntity::toDomain);
    }

    @Override
    public CouponIssueRequest insert(CouponIssueRequest request) {
        // saveAndFlush: the INSERT must reach Postgres inside the caller's try-block
        // so a UNIQUE (promotion_id, idempotency_key) violation arrives as a
        // DataIntegrityViolationException it can translate to 409, before any
        // coupon is minted.
        return jpaRepository.saveAndFlush(CouponIssueRequestJpaEntity.fromDomain(request))
                .toDomain();
    }
}
