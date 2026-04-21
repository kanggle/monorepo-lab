package com.example.review.domain.exception;

import java.util.UUID;

public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException(UUID userId, UUID productId) {
        super("User " + userId + " already reviewed product " + productId);
    }
}
