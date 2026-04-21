package com.example.review.domain.event;

public record ReviewDeletedPayload(
        String reviewId,
        String productId,
        String userId,
        String deletedAt
) implements ReviewEventPayload {
}
