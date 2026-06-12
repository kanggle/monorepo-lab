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
        String sellerId
) {
    public ProductSummary(UUID id, String name, ProductStatus status, long price, UUID categoryId) {
        this(id, name, status, price, null, categoryId, null);
    }
}
