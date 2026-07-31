package com.example.review.application.result;

import com.example.common.page.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Composes the shared {@link PageResult} paging carrier with the two
 * product-review-specific aggregate fields ({@code averageRating}/{@code totalReviews})
 * that are NOT part of {@code PageResult}'s shape (ADR-MONO-058 D3 — TASK-BE-567).
 * Delegate accessors preserve the previously-flat call surface for existing consumers.
 */
public record ReviewListResult(
        PageResult<ReviewItem> pageResult,
        double averageRating,
        long totalReviews
) {
    public List<ReviewItem> content() {
        return pageResult.content();
    }

    public int page() {
        return pageResult.page();
    }

    public int size() {
        return pageResult.size();
    }

    public long totalElements() {
        return pageResult.totalElements();
    }

    public int totalPages() {
        return pageResult.totalPages();
    }

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
