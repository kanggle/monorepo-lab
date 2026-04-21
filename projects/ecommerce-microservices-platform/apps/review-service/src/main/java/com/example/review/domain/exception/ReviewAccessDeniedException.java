package com.example.review.domain.exception;

import java.util.UUID;

public class ReviewAccessDeniedException extends RuntimeException {

    public ReviewAccessDeniedException(UUID userId, UUID reviewId) {
        super("User " + userId + " is not the author of review " + reviewId);
    }
}
