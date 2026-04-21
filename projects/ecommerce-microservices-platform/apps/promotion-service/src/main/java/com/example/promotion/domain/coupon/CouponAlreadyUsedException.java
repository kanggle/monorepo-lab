package com.example.promotion.domain.coupon;

public class CouponAlreadyUsedException extends RuntimeException {

    public CouponAlreadyUsedException(String couponId) {
        super("Coupon has already been used: " + couponId);
    }
}
