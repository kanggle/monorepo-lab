package com.example.promotion.domain.promotion;

public class PromotionHasIssuedCouponsException extends RuntimeException {

    public PromotionHasIssuedCouponsException(String promotionId) {
        super("Cannot delete a promotion with issued coupons: " + promotionId);
    }
}
