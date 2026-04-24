package com.example.review.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateReviewRequest(
        @NotNull(message = "productId must not be null")
        UUID productId,

        String productName,

        @Min(value = 1, message = "rating must be between 1 and 5")
        @Max(value = 5, message = "rating must be between 1 and 5")
        int rating,

        @NotBlank(message = "title must not be blank")
        String title,

        @NotBlank(message = "content must not be blank")
        String content
) {
}
