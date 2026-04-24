package com.example.promotion.domain.promotion;

public class CouponLimitExceededException extends RuntimeException {

    public CouponLimitExceededException(String promotionId, int max, int issued, int requested) {
        super("Coupon limit exceeded for promotion " + promotionId
                + ": max=" + max + ", issued=" + issued + ", requested=" + requested);
    }
}
