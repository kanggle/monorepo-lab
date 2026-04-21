package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.CreatePromotionResult;

public record CreatePromotionResponse(String promotionId) {

    public static CreatePromotionResponse from(CreatePromotionResult result) {
        return new CreatePromotionResponse(result.promotionId());
    }
}
