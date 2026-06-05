package com.example.auth.domain.token;

import java.util.Objects;

/**
 * Value object representing an access + refresh token pair.
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {

    public TokenPair {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
    }
}
