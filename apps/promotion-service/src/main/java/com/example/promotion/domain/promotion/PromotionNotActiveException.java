package com.example.promotion.domain.promotion;

public class PromotionNotActiveException extends RuntimeException {

    public PromotionNotActiveException(String promotionId) {
        super("Promotion is not currently active: " + promotionId);
    }
}
