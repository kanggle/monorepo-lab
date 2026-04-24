package com.example.product.domain.event;

import java.util.List;

public record ProductImagesUpdatedPayload(
        String productId,
        String thumbnailUrl,
        List<ImageSnapshot> images
) implements EventPayload {

    public record ImageSnapshot(
            String imageId,
            String objectKey,
            String url,
            int sortOrder,
            boolean isPrimary
    ) {}

    public static ProductImagesUpdatedPayload of(String productId, String thumbnailUrl, List<ImageSnapshot> images) {
        return new ProductImagesUpdatedPayload(productId, thumbnailUrl, images);
    }
}
