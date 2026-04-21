package com.example.product.presentation.dto;

import com.example.product.application.command.UpdateProductCommand;
import com.example.product.domain.model.ProductStatus;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record UpdateProductRequest(
        String name,
        String description,
        @Min(value = 0, message = "가격은 0 이상이어야 합니다")
        Long price,
        ProductStatus status,
        String thumbnailUrl
) {
    public UpdateProductRequest(String name, String description, Long price, ProductStatus status) {
        this(name, description, price, status, null);
    }

    public UpdateProductCommand toCommand(UUID productId) {
        return new UpdateProductCommand(productId, name, description, price, status, thumbnailUrl);
    }
}
