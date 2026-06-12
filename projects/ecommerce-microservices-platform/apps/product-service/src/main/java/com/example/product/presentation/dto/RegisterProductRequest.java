package com.example.product.presentation.dto;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record RegisterProductRequest(
        @NotBlank(message = "상품명은 필수입니다") String name,
        String description,
        @Positive(message = "가격은 양수여야 합니다") long price,
        UUID categoryId,
        String thumbnailUrl,
        // Optional owning seller (OPERATOR surface, ADR-MONO-030 §3.2). Absent →
        // resolved from the seller-scope claim, else the tenant default seller (D8).
        String sellerId,
        @NotEmpty(message = "variants는 하나 이상 필요합니다") @Valid List<RegisterVariantRequest> variants
) {
    public RegisterProductCommand toCommand() {
        List<VariantCommand> variantCommands = variants.stream()
                .map(v -> new VariantCommand(v.optionName(), v.stock(), v.additionalPrice()))
                .toList();
        return new RegisterProductCommand(name, description, price, categoryId, thumbnailUrl, sellerId, variantCommands);
    }
}
