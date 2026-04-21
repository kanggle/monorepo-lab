package com.example.product.application.command;

import com.example.product.domain.model.ProductStatus;

import java.util.UUID;

public record UpdateProductCommand(
        UUID productId,
        String name,
        String description,
        Long price,
        ProductStatus status,
        String thumbnailUrl
) {
    public UpdateProductCommand(UUID productId, String name, String description, Long price,
                                ProductStatus status) {
        this(productId, name, description, price, status, null);
    }
}
