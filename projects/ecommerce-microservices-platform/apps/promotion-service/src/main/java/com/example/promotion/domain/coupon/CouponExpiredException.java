package com.example.promotion.domain.coupon;

public class CouponExpiredException extends RuntimeException {

    public CouponExpiredException(String couponId) {
        super("Coupon has expired: " + couponId);
    }
}
