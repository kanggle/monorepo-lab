package com.example.product.presentation.dto;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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
        @NotEmpty(message = "variants는 하나 이상 필요합니다") @Valid List<RegisterVariantRequest> variants,
        // Optional fan artist id (ADR-MONO-079 D3) — no FK; blank/absent = no collection.
        @Size(max = 64, message = "collectionRef 는 64자 이하여야 합니다") String collectionRef
) {
    public RegisterProductRequest(String name, String description, long price, UUID categoryId,
                                  String thumbnailUrl, String sellerId,
                                  List<RegisterVariantRequest> variants) {
        this(name, description, price, categoryId, thumbnailUrl, sellerId, variants, null);
    }

    public RegisterProductCommand toCommand(String idempotencyKey) {
        List<VariantCommand> variantCommands = variants.stream()
                .map(v -> new VariantCommand(v.optionName(), v.stock(), v.additionalPrice()))
                .toList();
        return new RegisterProductCommand(
                name, description, price, categoryId, thumbnailUrl, sellerId, variantCommands, idempotencyKey,
                collectionRef);
    }
}
