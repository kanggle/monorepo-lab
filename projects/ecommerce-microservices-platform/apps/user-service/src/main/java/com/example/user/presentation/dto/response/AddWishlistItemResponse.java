package com.example.user.presentation.dto.response;

import com.example.user.application.result.AddWishlistItemResult;

import java.util.UUID;

public record AddWishlistItemResponse(
        UUID wishlistItemId,
        UUID productId
) {
    public static AddWishlistItemResponse from(AddWishlistItemResult result) {
        return new AddWishlistItemResponse(result.wishlistItemId(), result.productId());
    }
}
