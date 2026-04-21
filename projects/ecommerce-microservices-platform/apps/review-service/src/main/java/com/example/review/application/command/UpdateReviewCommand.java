package com.example.review.application.command;

import java.util.UUID;

public record UpdateReviewCommand(
        UUID userId,
        UUID reviewId,
        int rating,
        String title,
        String content
) {
}
