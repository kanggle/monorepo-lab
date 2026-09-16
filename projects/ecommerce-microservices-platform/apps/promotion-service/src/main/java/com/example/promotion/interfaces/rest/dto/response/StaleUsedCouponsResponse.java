package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.domain.coupon.StaleUsedCoupon;

import java.time.Instant;
import java.util.List;

/** Response body for {@code POST /api/internal/coupons/stale-used} (TASK-INT-028). */
public record StaleUsedCouponsResponse(List<Item> coupons) {

    public record Item(String couponId, String orderId, String tenantId, Instant usedAt) {
    }

    public static StaleUsedCouponsResponse from(List<StaleUsedCoupon> coupons) {
        return new StaleUsedCouponsResponse(coupons.stream()
                .map(c -> new Item(c.couponId(), c.orderId(), c.tenantId(), c.usedAt()))
                .toList());
    }
}
