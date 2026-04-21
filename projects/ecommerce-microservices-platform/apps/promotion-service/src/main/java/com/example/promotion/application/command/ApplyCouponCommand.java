package com.example.promotion.application.command;

public record ApplyCouponCommand(
        String couponId,
        String userId,
        String orderId,
        long orderAmount
) {
}
