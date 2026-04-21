package com.example.product.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignedUrlRequest(
        @NotBlank(message = "contentType is required") String contentType,
        @Positive(message = "contentLength must be positive") long contentLength
) {}
