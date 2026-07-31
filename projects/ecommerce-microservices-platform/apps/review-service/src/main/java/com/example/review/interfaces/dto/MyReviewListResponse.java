package com.example.review.interfaces.dto;

import com.example.common.page.PageResult;
import com.example.review.application.result.MyReviewItem;

import java.util.List;

public record MyReviewListResponse(
        List<MyReviewItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record MyReviewItemResponse(
            String reviewId,
            String productId,
            String productName,
            int rating,
            String title,
            String content,
            String createdAt
    ) {}

    public static MyReviewListResponse from(PageResult<MyReviewItem> result) {
        List<MyReviewItemResponse> items = result.content().stream()
                .map(item -> new MyReviewItemResponse(
                        item.reviewId().toString(),
                        item.productId().toString(),
                        item.productName(),
                        item.rating(),
                        item.title(),
                        item.content(),
                        item.createdAt().toString()
                ))
                .toList();

        return new MyReviewListResponse(items, result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
