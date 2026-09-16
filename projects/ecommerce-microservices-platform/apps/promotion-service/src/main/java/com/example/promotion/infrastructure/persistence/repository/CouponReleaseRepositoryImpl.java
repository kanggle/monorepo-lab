package com.example.promotion.infrastructure.persistence.repository;

import com.example.promotion.domain.coupon.CouponRelease;
import com.example.promotion.domain.coupon.CouponReleaseRepository;
import com.example.promotion.infrastructure.persistence.entity.CouponReleaseJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponReleaseRepositoryImpl implements CouponReleaseRepository {

    private final CouponReleaseJpaRepository jpaRepository;

    @Override
    public boolean existsFor(String couponId, String orderId) {
        return jpaRepository.existsByCouponIdAndOrderId(couponId, orderId);
    }

    @Override
    public CouponRelease record(CouponRelease release) {
        // saveAndFlush: the INSERT reaches Postgres inside the caller's transaction, so a
        // violation of UNIQUE (coupon_id, order_id) surfaces here rather than at commit. The
        // caller checks existsFor first while holding the coupon row's lock, so this is the
        // backstop, not the normal path.
        return jpaRepository.saveAndFlush(CouponReleaseJpaEntity.fromDomain(release)).toDomain();
    }
}
