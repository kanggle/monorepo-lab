package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.ApplyCouponResult;

public record ApplyCouponResponse(String couponId, long discountAmount, long finalAmount) {

    public static ApplyCouponResponse from(ApplyCouponResult result) {
        return new ApplyCouponResponse(result.couponId(), result.discountAmount(), result.finalAmount());
    }
}
