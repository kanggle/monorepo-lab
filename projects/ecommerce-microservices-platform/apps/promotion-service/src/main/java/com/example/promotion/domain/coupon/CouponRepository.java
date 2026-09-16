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

    /**
     * Resolves a coupon row's outer-axis tenant by its (globally-unique) id, for the
     * batch expiry path to stamp the CouponExpired envelope with the coupon's own
     * tenant (M5). Tenant is a persistence/event concern — it is surfaced as a bare
     * {@code String} so the {@code Coupon} domain aggregate stays tenant-free. Returns
     * {@code null} when the coupon is absent (caller maps null → default tenant).
     */
    String findTenantIdByCouponId(String couponId);

    /**
     * Coupons {@code USED} before {@code usedBefore} that name an order, oldest first, across every
     * tenant (TASK-INT-028). A system sweep with no request tenant, like the expiry sweep. Rows with
     * no {@code order_id} are excluded: there is no order to ask about, and "cannot judge" must
     * never become a release.
     */
    List<StaleUsedCoupon> findStaleUsedCoupons(Instant usedBefore, int limit);
}
