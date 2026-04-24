package com.example.promotion.domain.coupon;

public class CouponNotOwnedException extends RuntimeException {

    public CouponNotOwnedException(String couponId, String userId) {
        super("Coupon " + couponId + " does not belong to user " + userId);
    }
}
