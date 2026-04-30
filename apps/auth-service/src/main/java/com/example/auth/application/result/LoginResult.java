package com.example.auth.application.result;

public record LoginResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static LoginResult of(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResult(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
