package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.PromotionDetail;

public record PromotionDetailResponse(
        String promotionId,
        String name,
        String description,
        String discountType,
        long discountValue,
        long maxDiscountAmount,
        int maxIssuanceCount,
        int issuedCount,
        String startDate,
        String endDate,
        String status,
        String createdAt,
        String updatedAt
) {
    public static PromotionDetailResponse from(PromotionDetail detail) {
        return new PromotionDetailResponse(
                detail.promotionId(),
                detail.name(),
                detail.description(),
                detail.discountType().name(),
                detail.discountValue(),
                detail.maxDiscountAmount(),
                detail.maxIssuanceCount(),
                detail.issuedCount(),
                detail.startDate().toString(),
                detail.endDate().toString(),
                detail.status().name(),
                detail.createdAt().toString(),
                detail.updatedAt().toString()
        );
    }
}
