package com.example.promotion.domain.promotion;

public class PromotionNotFoundException extends RuntimeException {

    public PromotionNotFoundException(String promotionId) {
        super("Promotion not found: " + promotionId);
    }
}
