package com.example.product.presentation.dto;

import com.example.product.domain.port.PresignedUploadResult;

import java.time.Instant;

public record PresignedUrlResponse(
        String uploadUrl,
        String objectKey,
        Instant expiresAt
) {
    public static PresignedUrlResponse from(PresignedUploadResult result) {
        return new PresignedUrlResponse(result.uploadUrl(), result.objectKey(), result.expiresAt());
    }
}
