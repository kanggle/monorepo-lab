package com.example.review.interfaces.dto;

import com.example.review.application.result.ReviewSummaryResult;

import java.util.Map;

public record ReviewSummaryResponse(
        String productId,
        double averageRating,
        long totalReviews,
        Map<Integer, Long> ratingDistribution
) {
    public static ReviewSummaryResponse from(ReviewSummaryResult result) {
        return new ReviewSummaryResponse(
                result.productId().toString(),
                result.averageRating(),
                result.totalReviews(),
                result.ratingDistribution()
        );
    }
}
