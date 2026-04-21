package com.example.review.domain.exception;

import java.util.UUID;

public class ProductNotPurchasedException extends RuntimeException {

    public ProductNotPurchasedException(UUID userId, UUID productId) {
        super("User " + userId + " has not purchased product " + productId);
    }
}
