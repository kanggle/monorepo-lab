package com.example.review.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyReviewListResult(
        List<MyReviewItem> content,
        int page,
        int size,
        long totalElements
) {
    public record MyReviewItem(
            UUID reviewId,
            UUID productId,
            String productName,
            int rating,
            String title,
            String content,
            Instant createdAt
    ) {}
}
