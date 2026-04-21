package com.example.review.application.result;

import java.util.Map;
import java.util.UUID;

public record ReviewSummaryResult(
        UUID productId,
        double averageRating,
        long totalReviews,
        Map<Integer, Long> ratingDistribution
) {
}
