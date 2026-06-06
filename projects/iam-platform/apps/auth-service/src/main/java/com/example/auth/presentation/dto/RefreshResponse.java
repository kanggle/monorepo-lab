package com.example.auth.presentation.dto;

import com.example.auth.application.result.RefreshTokenResult;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static RefreshResponse from(RefreshTokenResult result) {
        return new RefreshResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.tokenType()
        );
    }
}
