package com.example.product.application.command;

import com.example.product.domain.model.ProductStatus;

import java.util.UUID;

public record UpdateProductCommand(
        UUID productId,
        String name,
        String description,
        Long price,
        ProductStatus status,
        String thumbnailUrl,
        /**
         * Fan artist collection (ADR-MONO-079 D3). {@code null} = unchanged;
         * blank = clear to {@code NULL}; otherwise trimmed and stored.
         */
        String collectionRef
) {
    public UpdateProductCommand(UUID productId, String name, String description, Long price,
                                ProductStatus status) {
        this(productId, name, description, price, status, null, null);
    }

    public UpdateProductCommand(UUID productId, String name, String description, Long price,
                                ProductStatus status, String thumbnailUrl) {
        this(productId, name, description, price, status, thumbnailUrl, null);
    }
}
