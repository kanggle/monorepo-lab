package com.example.user.presentation.dto.response;

import com.example.user.application.result.WishlistItemResult;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID wishlistItemId,
        UUID productId,
        String productName,
        int productPrice,
        String productStatus,
        Instant addedAt
) {
    public static WishlistItemResponse from(WishlistItemResult result) {
        return new WishlistItemResponse(
                result.wishlistItemId(),
                result.productId(),
                result.productName(),
                result.productPrice(),
                result.productStatus(),
                result.addedAt()
        );
    }
}
