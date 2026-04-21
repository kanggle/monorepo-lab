package com.example.user.domain.exception;

import java.util.UUID;

public class AlreadyInWishlistException extends RuntimeException {

    public AlreadyInWishlistException(UUID productId) {
        super("Product is already in the wishlist: productId=" + productId);
    }
}
