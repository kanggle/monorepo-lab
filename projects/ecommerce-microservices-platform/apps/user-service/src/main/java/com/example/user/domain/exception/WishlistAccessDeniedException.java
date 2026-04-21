package com.example.user.domain.exception;

import java.util.UUID;

public class WishlistAccessDeniedException extends RuntimeException {

    public WishlistAccessDeniedException(UUID wishlistItemId) {
        super("Access denied: not the wishlist owner for wishlistItemId=" + wishlistItemId);
    }
}
