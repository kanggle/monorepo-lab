package com.example.user.application.result;

import com.example.user.domain.model.WishlistItem;

import java.util.UUID;

public record AddWishlistItemResult(
        UUID wishlistItemId,
        UUID productId
) {
    public static AddWishlistItemResult from(WishlistItem item) {
        return new AddWishlistItemResult(item.getId(), item.getProductId());
    }
}
