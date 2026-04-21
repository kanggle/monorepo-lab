package com.example.promotion.domain.promotion;

public class PromotionAlreadyEndedException extends RuntimeException {

    public PromotionAlreadyEndedException(String promotionId) {
        super("Cannot update an ended promotion: " + promotionId);
    }
}
