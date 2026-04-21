package com.example.product.presentation.dto;

import com.example.product.domain.model.ProductImage;

import java.time.Instant;

public record RegisterImageResponse(
        String imageId,
        String objectKey,
        int sortOrder,
        boolean isPrimary,
        String url,
        Instant uploadedAt
) {
    public static RegisterImageResponse from(ProductImage image, String resolvedUrl) {
        return new RegisterImageResponse(
                image.getId().toString(),
                image.getObjectKey(),
                image.getSortOrder(),
                image.isPrimary(),
                resolvedUrl,
                image.getUploadedAt()
        );
    }
}
