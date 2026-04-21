package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.UpdatePromotionResult;

public record UpdatePromotionResponse(String promotionId) {

    public static UpdatePromotionResponse from(UpdatePromotionResult result) {
        return new UpdatePromotionResponse(result.promotionId());
    }
}
