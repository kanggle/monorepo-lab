package com.example.user.application.result;

import java.time.Instant;
import java.util.UUID;

public record WishlistItemResult(
        UUID wishlistItemId,
        UUID productId,
        String productName,
        int productPrice,
        String productStatus,
        Instant addedAt
) {
}
