package com.example.review.interfaces.dto;

import com.example.review.application.result.ReviewListResult;

import java.util.List;

public record ReviewListResponse(
        List<ReviewItemResponse> content,
        int page,
        int size,
        long totalElements,
        double averageRating,
        long totalReviews
) {
    public record ReviewItemResponse(
            String reviewId,
            String userId,
            int rating,
            String title,
            String content,
            String createdAt,
            String updatedAt
    ) {}

    public static ReviewListResponse from(ReviewListResult result) {
        List<ReviewItemResponse> items = result.content().stream()
                .map(item -> new ReviewItemResponse(
                        item.reviewId().toString(),
                        item.userId().toString(),
                        item.rating(),
                        item.title(),
                        item.content(),
                        item.createdAt().toString(),
                        item.updatedAt().toString()
                ))
                .toList();

        return new ReviewListResponse(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.averageRating(),
                result.totalReviews()
        );
    }
}
