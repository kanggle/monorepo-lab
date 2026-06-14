package com.example.product.presentation.dto;

import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;

import java.time.Instant;
import java.util.List;

/**
 * Operator seller-list envelope (ADR-MONO-030 Step 4 facet f). Mirrors
 * {@link ProductListResponse}: {@code content[]}, {@code page}, {@code size},
 * {@code totalElements}.
 */
public record SellerListResponse(
        List<SellerSummaryItem> content,
        int page,
        int size,
        long totalElements
) {
    public record SellerSummaryItem(
            String sellerId,
            String displayName,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static SellerListResponse from(SellerListResult result) {
        List<SellerSummaryItem> items = result.content().stream()
                .map(SellerListResponse::toItem)
                .toList();
        return new SellerListResponse(items, result.page(), result.size(), result.totalElements());
    }

    private static SellerSummaryItem toItem(SellerSummary summary) {
        return new SellerSummaryItem(
                summary.sellerId(),
                summary.displayName(),
                summary.status().name(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
