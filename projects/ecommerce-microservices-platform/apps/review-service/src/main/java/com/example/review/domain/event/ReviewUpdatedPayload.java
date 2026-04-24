package com.example.review.domain.event;

public record ReviewUpdatedPayload(
        String reviewId,
        String productId,
        String userId,
        int rating,
        String updatedAt
) implements ReviewEventPayload {
}
