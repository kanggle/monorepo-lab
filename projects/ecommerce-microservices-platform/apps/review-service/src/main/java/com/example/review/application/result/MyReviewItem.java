package com.example.review.application.result;

import java.time.Instant;
import java.util.UUID;

public record MyReviewItem(
        UUID reviewId,
        UUID productId,
        String productName,
        int rating,
        String title,
        String content,
        Instant createdAt
) {}
