package com.example.product.presentation.dto;

import com.example.product.application.dto.SellerSummary;

import java.time.Instant;

/**
 * Seller detail response (ADR-MONO-030 Step 4 facet f). v1 is ACTIVE-only so the
 * detail shape equals the summary row (sellerId, displayName, status, timestamps);
 * a dedicated record keeps room for richer detail fields without reshaping the list.
 */
public record SellerResponse(
        String sellerId,
        String displayName,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SellerResponse from(SellerSummary seller) {
        return new SellerResponse(
                seller.sellerId(),
                seller.displayName(),
                seller.status().name(),
                seller.createdAt(),
                seller.updatedAt());
    }
}
