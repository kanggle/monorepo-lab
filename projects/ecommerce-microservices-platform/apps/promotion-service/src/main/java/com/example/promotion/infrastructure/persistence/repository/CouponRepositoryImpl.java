package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.common.page.PageResult;
import com.example.promotion.infrastructure.persistence.entity.CouponJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    @Override
    public Coupon save(Coupon coupon) {
        Optional<CouponJpaEntity> existing = jpaRepository.findById(coupon.getCouponId());
        if (existing.isPresent()) {
            existing.get().updateFrom(coupon);
            return existing.get().toDomain();
        }
        CouponJpaEntity entity = CouponJpaEntity.fromDomain(coupon);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<Coupon> saveAll(List<Coupon> coupons) {
        List<CouponJpaEntity> entities = coupons.stream()
                .map(CouponJpaEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Coupon> findById(String couponId) {
        return jpaRepository.findById(couponId).map(CouponJpaEntity::toDomain);
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(String couponId) {
        return jpaRepository.findByIdForUpdate(couponId).map(CouponJpaEntity::toDomain);
    }

    @Override
    public PageResult<Coupon> findByUserId(String userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"));
        Page<CouponJpaEntity> result = jpaRepository.findByUserId(userId, pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public PageResult<Coupon> findByUserIdAndStatus(String userId, CouponStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"));
        Page<CouponJpaEntity> result = jpaRepository.findByUserIdAndStatus(userId, status, pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public List<Coupon> findExpiredIssuedCoupons(Instant now, int batchSize) {
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        return jpaRepository.findExpiredIssuedCoupons(now, pageRequest).stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Coupon> findByOrderIdAndStatus(String orderId, CouponStatus status) {
        return jpaRepository.findByOrderIdAndStatus(orderId, status).stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByPromotionId(String promotionId) {
        return jpaRepository.existsByPromotionId(promotionId);
    }

    private PageResult<Coupon> toPageResult(Page<CouponJpaEntity> result, int page, int size) {
        return new PageResult<>(
                result.getContent().stream().map(CouponJpaEntity::toDomain).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
