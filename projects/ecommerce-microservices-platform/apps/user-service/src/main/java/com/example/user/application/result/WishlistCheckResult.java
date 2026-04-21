package com.example.user.application.result;

import java.util.UUID;

public record WishlistCheckResult(
        UUID productId,
        boolean inWishlist,
        UUID wishlistItemId
) {
}
