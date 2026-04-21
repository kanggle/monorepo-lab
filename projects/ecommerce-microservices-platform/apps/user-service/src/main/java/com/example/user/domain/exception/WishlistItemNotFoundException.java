package com.example.user.domain.exception;

import java.util.UUID;

public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(UUID wishlistItemId) {
        super("Wishlist item not found: wishlistItemId=" + wishlistItemId);
    }
}
