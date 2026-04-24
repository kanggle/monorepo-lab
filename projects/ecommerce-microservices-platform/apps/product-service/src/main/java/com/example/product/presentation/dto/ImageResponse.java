package com.example.product.presentation.dto;

import com.example.product.domain.model.ProductImage;

public record ImageResponse(
        String imageId,
        String objectKey,
        int sortOrder,
        boolean isPrimary,
        String url,
        String uploadedAt
) {
    public static ImageResponse from(ProductImage image, String resolvedUrl) {
        return new ImageResponse(
                image.getId().toString(),
                image.getObjectKey(),
                image.getSortOrder(),
                image.isPrimary(),
                resolvedUrl,
                image.getUploadedAt().toString()
        );
    }
}
