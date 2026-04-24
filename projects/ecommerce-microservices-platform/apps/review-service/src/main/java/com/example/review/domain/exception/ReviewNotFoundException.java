package com.example.review.domain.exception;

import java.util.UUID;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(UUID reviewId) {
        super("Review not found: " + reviewId);
    }
}
