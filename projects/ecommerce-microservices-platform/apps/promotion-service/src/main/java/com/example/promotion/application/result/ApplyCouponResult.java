package com.example.promotion.application.result;

public record ApplyCouponResult(
        String couponId,
        long discountAmount,
        long finalAmount
) {
}
