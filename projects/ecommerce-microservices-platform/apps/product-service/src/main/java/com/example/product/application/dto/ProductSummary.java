package com.example.product.application.dto;

import com.example.product.domain.model.ProductStatus;

import java.util.UUID;

public record ProductSummary(
        UUID id,
        String name,
        ProductStatus status,
        long price,
        String thumbnailUrl,
        UUID categoryId,
        String sellerId,
        /** Fan artist id (ADR-MONO-079 D3); {@code null} = no collection. */
        String collectionRef
) {
    public ProductSummary(UUID id, String name, ProductStatus status, long price, UUID categoryId) {
        this(id, name, status, price, null, categoryId, null, null);
    }

    public ProductSummary(UUID id, String name, ProductStatus status, long price, String thumbnailUrl,
                          UUID categoryId, String sellerId) {
        this(id, name, status, price, thumbnailUrl, categoryId, sellerId, null);
    }
}
