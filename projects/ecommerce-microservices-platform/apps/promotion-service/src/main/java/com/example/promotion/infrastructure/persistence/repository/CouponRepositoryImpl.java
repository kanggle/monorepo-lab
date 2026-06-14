package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.common.page.PageResult;
import com.example.promotion.domain.tenant.TenantContext;
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
        // On insert, stamp the request tenant; on update (apply/restore/expire), the
        // existing row's tenant_id is preserved (updatable=false), so a saga/batch
        // mutation never re-tenants the coupon.
        Optional<CouponJpaEntity> existing = jpaRepository.findById(coupon.getCouponId());
        if (existing.isPresent()) {
            existing.get().updateFrom(coupon);
            return existing.get().toDomain();
        }
        CouponJpaEntity entity = CouponJpaEntity.fromDomain(coupon, TenantContext.currentTenant());
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public List<Coupon> saveAll(List<Coupon> coupons) {
        // Issue path: every coupon belongs to the request tenant (= the parent
        // promotion's tenant, which the issue command loaded tenant-scoped).
        String tenantId = TenantContext.currentTenant();
        List<CouponJpaEntity> entities = coupons.stream()
                .map(coupon -> CouponJpaEntity.fromDomain(coupon, tenantId))
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
        // Coupon apply (HTTP): tenant-scoped lock so a cross-tenant apply cannot reach
        // the row → 404 (M3).
        return jpaRepository.findByIdForUpdate(couponId, TenantContext.currentTenant())
                .map(CouponJpaEntity::toDomain);
    }

    @Override
    public PageResult<Coupon> findByUserId(String userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"));
        Page<CouponJpaEntity> result = jpaRepository.findByTenantIdAndUserId(
                TenantContext.currentTenant(), userId, pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public PageResult<Coupon> findByUserIdAndStatus(String userId, CouponStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"));
        Page<CouponJpaEntity> result = jpaRepository.findByTenantIdAndUserIdAndStatus(
                TenantContext.currentTenant(), userId, status, pageRequest);
        return toPageResult(result, page, size);
    }

    @Override
    public List<Coupon> findExpiredIssuedCoupons(Instant now, int batchSize) {
        // TENANT-AGNOSTIC: global operational expiry sweep on a scheduler thread (no
        // request tenant). Each row's tenant is preserved on expire and carried onto
        // the CouponExpired envelope via findTenantIdByCouponId.
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        return jpaRepository.findExpiredIssuedCoupons(now, pageRequest).stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Coupon> findByOrderIdAndStatus(String orderId, CouponStatus status) {
        // TENANT-AGNOSTIC: OrderCancelled recovery keyed by the globally-unique
        // orderId (system saga path); the consumer also binds the envelope tenant.
        return jpaRepository.findByOrderIdAndStatus(orderId, status).stream()
                .map(CouponJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByPromotionId(String promotionId) {
        return jpaRepository.existsByPromotionId(promotionId);
    }

    @Override
    public String findTenantIdByCouponId(String couponId) {
        return jpaRepository.findTenantIdByCouponId(couponId).orElse(null);
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
