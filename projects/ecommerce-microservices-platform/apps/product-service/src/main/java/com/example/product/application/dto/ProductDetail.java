package com.example.product.application.dto;

import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;

import java.util.List;
import java.util.UUID;

public record ProductDetail(
        UUID id,
        String name,
        String description,
        ProductStatus status,
        long price,
        UUID categoryId,
        String thumbnailUrl,
        String sellerId,
        /** Fan artist id (ADR-MONO-079 D3); {@code null} = no collection. */
        String collectionRef,
        List<VariantDetail> variants
) {
    public ProductDetail(UUID id, String name, String description, ProductStatus status, long price,
                         UUID categoryId, List<VariantDetail> variants) {
        this(id, name, description, status, price, categoryId, null, null, null, variants);
    }

    public ProductDetail(UUID id, String name, String description, ProductStatus status, long price,
                         UUID categoryId, String thumbnailUrl, String sellerId,
                         List<VariantDetail> variants) {
        this(id, name, description, status, price, categoryId, thumbnailUrl, sellerId, null, variants);
    }

    public static ProductDetail from(Product product) {
        List<VariantDetail> variants = product.getVariants().stream()
                .map(VariantDetail::from)
                .toList();

        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getStatus(),
                product.getPrice().value(),
                product.getCategoryId(),
                product.getThumbnailUrl(),
                product.getSellerId(),
                product.getCollectionRef(),
                variants);
    }
}
