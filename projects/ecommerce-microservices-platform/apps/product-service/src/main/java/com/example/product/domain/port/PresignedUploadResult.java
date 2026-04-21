package com.example.product.domain.port;

import java.time.Instant;

public record PresignedUploadResult(
        String uploadUrl,
        String objectKey,
        Instant expiresAt
) {}
