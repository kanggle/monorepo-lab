package com.example.review.domain.model;

public record Rating(int value) {

    public static final int MIN = 1;
    public static final int MAX = 5;

    public Rating {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(
                    "Rating must be between " + MIN + " and " + MAX + ", but was: " + value);
        }
    }
}
