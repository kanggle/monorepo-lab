package com.example.review.application.command;

import java.util.UUID;

public record CreateReviewCommand(
        UUID userId,
        UUID productId,
        String productName,
        int rating,
        String title,
        String content
) {
}
