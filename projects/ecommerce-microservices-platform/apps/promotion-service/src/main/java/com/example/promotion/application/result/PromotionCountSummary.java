package com.example.promotion.application.result;

/**
 * Calendar-period-to-date promotion counts for the current tenant (KST boundaries).
 * Used by the console overview card (TASK-BE-468).
 */
public record PromotionCountSummary(
        long today,
        long week,
        long month,
        long total
) {
}
