package com.example.product.application.dto;

import com.example.product.domain.model.Seller;
import com.example.product.domain.model.SellerStatus;

import java.time.Instant;

/**
 * Operator read-surface summary row for a seller (ADR-MONO-030 Step 4 facet f).
 * Mirrors the {@code ProductSummary} list-row shape.
 */
public record SellerSummary(
        String sellerId,
        String displayName,
        SellerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SellerSummary from(Seller seller) {
        return new SellerSummary(
                seller.getSellerId(),
                seller.getDisplayName(),
                seller.getStatus(),
                seller.getCreatedAt(),
                seller.getUpdatedAt());
    }
}
