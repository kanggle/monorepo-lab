package com.example.product.application.dto;

import com.example.product.domain.model.ProductVariant;

import java.util.UUID;

public record VariantDetail(
        UUID id,
        String optionName,
        int stock,
        long additionalPrice
) {
    public static VariantDetail from(ProductVariant variant) {
        return new VariantDetail(
                variant.getId(),
                variant.getOptionName(),
                variant.getStock().value(),
                variant.getAdditionalPrice().value());
    }
}
