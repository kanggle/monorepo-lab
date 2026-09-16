package com.example.promotion.application.service;

import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.StaleUsedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Lists coupons that have been {@code USED} for a while, for batch-worker's orphan-coupon
 * reconciliation (TASK-INT-028, {@code promotion-api.md} § {@code POST /api/internal/coupons/stale-used}).
 *
 * <p>The {@link #MIN_OLDER_THAN_MINUTES} floor is enforced here as well as on the request: an order
 * placement still in flight must never have its coupon offered for release, whoever the caller is.
 */
@Service
@RequiredArgsConstructor
public class StaleUsedCouponQueryService {

    public static final int MIN_OLDER_THAN_MINUTES = 30;
    public static final int DEFAULT_OLDER_THAN_MINUTES = 60;
    public static final int DEFAULT_LIMIT = 200;
    public static final int MAX_LIMIT = 500;

    private final CouponRepository couponRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StaleUsedCoupon> find(int olderThanMinutes, int limit) {
        if (olderThanMinutes < MIN_OLDER_THAN_MINUTES) {
            throw new IllegalArgumentException("olderThanMinutes must be >= " + MIN_OLDER_THAN_MINUTES);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return couponRepository.findStaleUsedCoupons(
                clock.instant().minus(Duration.ofMinutes(olderThanMinutes)), limit);
    }
}
