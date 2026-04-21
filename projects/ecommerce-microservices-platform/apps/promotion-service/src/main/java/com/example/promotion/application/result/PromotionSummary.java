package com.example.promotion.application.result;

import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionStatus;

import java.time.Clock;
import java.time.Instant;

public record PromotionSummary(
        String promotionId,
        String name,
        DiscountType discountType,
        long discountValue,
        int maxIssuanceCount,
        int issuedCount,
        Instant startDate,
        Instant endDate,
        PromotionStatus status
) {
    public static PromotionSummary from(Promotion promotion, Clock clock) {
        return new PromotionSummary(
                promotion.getPromotionId(),
                promotion.getName(),
                promotion.getDiscountType(),
                promotion.getDiscountValue(),
                promotion.getMaxIssuanceCount(),
                promotion.getIssuedCount(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getStatus(clock)
        );
    }
}
