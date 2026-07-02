package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.PromotionCountSummary;

/**
 * Response for GET /api/promotions/summary — tenant-scoped KST calendar-period
 * promotion counts (TASK-BE-468).
 */
public record PromotionSummaryResponse(
        long today,
        long week,
        long month,
        long total
) {
    public static PromotionSummaryResponse from(PromotionCountSummary summary) {
        return new PromotionSummaryResponse(
                summary.today(),
                summary.week(),
                summary.month(),
                summary.total()
        );
    }
}
