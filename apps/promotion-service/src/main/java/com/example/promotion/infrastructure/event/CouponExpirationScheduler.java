package com.example.promotion.infrastructure.event;

import com.example.promotion.application.service.CouponExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpirationScheduler {

    private final CouponExpirationService couponExpirationService;

    @Scheduled(fixedDelay = 60000)
    public void expireCoupons() {
        int count = couponExpirationService.expireCoupons();
        if (count > 0) {
            log.info("Coupon expiration batch completed: expired={}", count);
        }
    }
}
