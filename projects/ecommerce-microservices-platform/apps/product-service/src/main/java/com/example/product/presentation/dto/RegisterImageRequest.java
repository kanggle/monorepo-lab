package com.example.product.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegisterImageRequest(
        @NotBlank(message = "objectKey is required") String objectKey,
        @Min(value = 0, message = "sortOrder must be non-negative") int sortOrder,
        boolean isPrimary
) {}
