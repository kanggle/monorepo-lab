package com.example.promotion.domain.coupon;

public class CouponRestoreNotAllowedException extends RuntimeException {

    public CouponRestoreNotAllowedException(String couponId) {
        super("Cannot restore expired coupon: " + couponId);
    }
}
