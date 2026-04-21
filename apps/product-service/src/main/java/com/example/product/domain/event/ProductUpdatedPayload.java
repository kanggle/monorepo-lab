package com.example.product.domain.event;

import com.example.product.domain.model.Product;

public record ProductUpdatedPayload(
        String productId,
        String name,
        String description,
        long price,
        String status,
        String categoryId,
        String thumbnailUrl
) implements EventPayload {

    public static ProductUpdatedPayload from(Product product) {
        return new ProductUpdatedPayload(
                product.getId().toString(),
                product.getName(),
                product.getDescription(),
                product.getPrice().value(),
                product.getStatus().name(),
                product.getCategoryId() != null ? product.getCategoryId().toString() : null,
                product.getThumbnailUrl());
    }
}
