package com.example.promotion.domain.coupon;

import com.example.common.page.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    List<Coupon> saveAll(List<Coupon> coupons);

    Optional<Coupon> findById(String couponId);

    Optional<Coupon> findByIdForUpdate(String couponId);

    PageResult<Coupon> findByUserId(String userId, int page, int size);

    PageResult<Coupon> findByUserIdAndStatus(String userId, CouponStatus status, int page, int size);

    List<Coupon> findExpiredIssuedCoupons(Instant now, int batchSize);

    List<Coupon> findByOrderIdAndStatus(String orderId, CouponStatus status);

    boolean existsByPromotionId(String promotionId);
}
