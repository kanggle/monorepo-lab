package com.example.auth.application.result;

public record RefreshTokenResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static RefreshTokenResult of(String accessToken, String refreshToken, long expiresIn) {
        return new RefreshTokenResult(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
