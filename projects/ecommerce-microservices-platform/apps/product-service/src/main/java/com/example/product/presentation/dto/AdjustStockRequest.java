package com.example.product.presentation.dto;

import com.example.product.application.command.AdjustStockCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID variantId,
        @NotNull Integer quantity,
        @NotBlank String reason
) {
    public AdjustStockCommand toCommand(UUID productId) {
        return new AdjustStockCommand(productId, variantId, quantity, reason);
    }
}
