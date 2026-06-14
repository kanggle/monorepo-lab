package com.example.promotion.application.service;

import com.example.promotion.application.event.CouponExpiredEvent;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponExpirationService {

    private static final int BATCH_SIZE = 100;

    private final CouponRepository couponRepository;
    private final PromotionEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public int expireCoupons() {
        Instant now = Instant.now(clock);
        List<Coupon> expiredCoupons = couponRepository.findExpiredIssuedCoupons(now, BATCH_SIZE);

        for (Coupon coupon : expiredCoupons) {
            coupon.expire(clock);
            couponRepository.save(coupon);

            // The expiry sweep is tenant-agnostic (global operational batch), but the
            // CouponExpired envelope must ride the expiring coupon's OWN row tenant
            // (M5) — not the ambient context (the scheduler thread has none). Resolve
            // it from the persisted row by id; null → default tenant (net-zero, D8).
            String tenantId = couponRepository.findTenantIdByCouponId(coupon.getCouponId());
            CouponExpiredEvent event = CouponExpiredEvent.of(
                    coupon.getCouponId(), coupon.getPromotionId(),
                    coupon.getUserId(), tenantId, clock
            );
            eventPublisher.publishCouponExpired(event);
        }

        if (!expiredCoupons.isEmpty()) {
            log.info("Expired {} coupons", expiredCoupons.size());
        }
        return expiredCoupons.size();
    }
}
