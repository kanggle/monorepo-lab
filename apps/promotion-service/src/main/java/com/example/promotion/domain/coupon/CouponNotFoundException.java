package com.example.promotion.domain.coupon;

public class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException(String couponId) {
        super("Coupon not found: " + couponId);
    }
}
