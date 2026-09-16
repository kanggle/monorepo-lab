package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.infrastructure.persistence.entity.CouponReleaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@code coupon_release} (TASK-INT-027). */
interface CouponReleaseJpaRepository extends JpaRepository<CouponReleaseJpaEntity, Long> {

    boolean existsByCouponIdAndOrderId(String couponId, String orderId);
}
