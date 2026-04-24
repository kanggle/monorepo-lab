package com.example.promotion.application.result;

import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionStatus;

import java.time.Clock;
import java.time.Instant;

public record PromotionDetail(
        String promotionId,
        String name,
        String description,
        DiscountType discountType,
        long discountValue,
        long maxDiscountAmount,
        int maxIssuanceCount,
        int issuedCount,
        Instant startDate,
        Instant endDate,
        PromotionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PromotionDetail from(Promotion promotion, Clock clock) {
        return new PromotionDetail(
                promotion.getPromotionId(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMaxDiscountAmount(),
                promotion.getMaxIssuanceCount(),
                promotion.getIssuedCount(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getStatus(clock),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt()
        );
    }
}
