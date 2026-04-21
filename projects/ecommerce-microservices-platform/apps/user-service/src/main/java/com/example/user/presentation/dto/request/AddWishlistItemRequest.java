package com.example.user.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWishlistItemRequest(
        @NotNull(message = "productId must not be null")
        UUID productId
) {
}
