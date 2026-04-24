package com.example.review.domain.event;

public record ReviewCreatedPayload(
        String reviewId,
        String productId,
        String userId,
        int rating,
        String createdAt
) implements ReviewEventPayload {
}
