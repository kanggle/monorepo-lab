package com.example.user.presentation.dto.response;

import com.example.user.application.result.WishlistCheckResult;

import java.util.UUID;

public record WishlistCheckResponse(
        UUID productId,
        boolean inWishlist,
        UUID wishlistItemId
) {
    public static WishlistCheckResponse from(WishlistCheckResult result) {
        return new WishlistCheckResponse(result.productId(), result.inWishlist(), result.wishlistItemId());
    }
}
