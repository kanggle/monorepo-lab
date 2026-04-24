package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductImage {

    private UUID id;
    private UUID productId;
    private String objectKey;
    private int sortOrder;
    private boolean isPrimary;
    private Instant uploadedAt;

    public static ProductImage create(UUID productId, String objectKey, int sortOrder, boolean isPrimary) {
        if (productId == null) throw new IllegalArgumentException("productId must not be null");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("objectKey must not be blank");
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");

        ProductImage image = new ProductImage();
        image.id = UUID.randomUUID();
        image.productId = productId;
        image.objectKey = objectKey;
        image.sortOrder = sortOrder;
        image.isPrimary = isPrimary;
        image.uploadedAt = Instant.now();
        return image;
    }

    public static ProductImage reconstitute(UUID id, UUID productId, String objectKey,
                                             int sortOrder, boolean isPrimary, Instant uploadedAt) {
        ProductImage image = new ProductImage();
        image.id = id;
        image.productId = productId;
        image.objectKey = objectKey;
        image.sortOrder = sortOrder;
        image.isPrimary = isPrimary;
        image.uploadedAt = uploadedAt;
        return image;
    }

    public void updateSortOrder(int sortOrder) {
        if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");
        this.sortOrder = sortOrder;
    }

    public void markPrimary() {
        this.isPrimary = true;
    }

    public void demotePrimary() {
        this.isPrimary = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductImage pi)) return false;
        return id != null && id.equals(pi.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
