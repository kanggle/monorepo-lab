package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.infrastructure.persistence.entity.CouponJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, String> {

    // ---- Tenant-scoped HTTP paths (ADR-MONO-030 M3; TASK-BE-368) ----------------

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponJpaEntity c WHERE c.couponId = :couponId AND c.tenantId = :tenantId")
    Optional<CouponJpaEntity> findByIdForUpdate(String couponId, String tenantId);

    Page<CouponJpaEntity> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    Page<CouponJpaEntity> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, CouponStatus status, Pageable pageable);

    boolean existsByPromotionId(String promotionId);

    @Query("SELECT c.tenantId FROM CouponJpaEntity c WHERE c.couponId = :couponId")
    Optional<String> findTenantIdByCouponId(String couponId);

    // ---- System / batch paths — intentionally TENANT-AGNOSTIC --------------------
    // Mirror order-service findByIdAcrossTenants: keyed off globally-unique ids or a
    // global operational sweep, so they must find the row regardless of ambient
    // context and can never reach the wrong tenant. The mutated row's tenant_id is
    // immutable (updatable=false), and the published event carries that row's tenant.

    /** Global expiry sweep — runs on a scheduler thread with no request tenant. */
    @Query("SELECT c FROM CouponJpaEntity c WHERE c.status = 'ISSUED' AND c.expiresAt <= :now ORDER BY c.expiresAt ASC")
    List<CouponJpaEntity> findExpiredIssuedCoupons(Instant now, Pageable pageable);

    /** OrderCancelled recovery — orderId is globally unique (system saga path). */
    List<CouponJpaEntity> findByOrderIdAndStatus(String orderId, CouponStatus status);
}
