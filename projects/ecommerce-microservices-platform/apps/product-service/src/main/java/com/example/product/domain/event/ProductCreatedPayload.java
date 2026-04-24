package com.example.product.domain.event;

import com.example.product.domain.model.Product;

import java.util.List;

public record ProductCreatedPayload(
        String productId,
        String name,
        String description,
        long price,
        String status,
        String categoryId,
        String thumbnailUrl,
        List<VariantPayload> variants
) implements EventPayload {

    public static ProductCreatedPayload from(Product product) {
        List<VariantPayload> variantPayloads = product.getVariants().stream()
                .map(v -> new VariantPayload(
                        v.getId().toString(),
                        v.getOptionName(),
                        v.getStock().value(),
                        v.getAdditionalPrice().value()))
                .toList();
        return new ProductCreatedPayload(
                product.getId().toString(),
                product.getName(),
                product.getDescription(),
                product.getPrice().value(),
                product.getStatus().name(),
                product.getCategoryId() != null ? product.getCategoryId().toString() : null,
                product.getThumbnailUrl(),
                variantPayloads);
    }

    public record VariantPayload(
            String variantId,
            String optionName,
            int stock,
            long additionalPrice
    ) {}
}
