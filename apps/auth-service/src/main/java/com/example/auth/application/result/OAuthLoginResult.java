package com.example.auth.application.result;

public record OAuthLoginResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        boolean isNewAccount
) {
}
