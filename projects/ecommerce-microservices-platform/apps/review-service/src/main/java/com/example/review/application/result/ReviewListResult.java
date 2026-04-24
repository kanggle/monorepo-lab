package com.example.review.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewListResult(
        List<ReviewItem> content,
        int page,
        int size,
        long totalElements,
        double averageRating,
        long totalReviews
) {
    public record ReviewItem(
            UUID reviewId,
            UUID userId,
            int rating,
            String title,
            String content,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
