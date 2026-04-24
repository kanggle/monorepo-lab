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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponJpaEntity c WHERE c.couponId = :couponId")
    Optional<CouponJpaEntity> findByIdForUpdate(String couponId);

    Page<CouponJpaEntity> findByUserId(String userId, Pageable pageable);

    Page<CouponJpaEntity> findByUserIdAndStatus(String userId, CouponStatus status, Pageable pageable);

    @Query("SELECT c FROM CouponJpaEntity c WHERE c.status = 'ISSUED' AND c.expiresAt <= :now ORDER BY c.expiresAt ASC")
    List<CouponJpaEntity> findExpiredIssuedCoupons(Instant now, Pageable pageable);

    List<CouponJpaEntity> findByOrderIdAndStatus(String orderId, CouponStatus status);

    boolean existsByPromotionId(String promotionId);
}
