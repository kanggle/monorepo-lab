package com.example.auth.presentation.dto;

import com.example.auth.application.result.OAuthLoginResult;

public record OAuthCallbackResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        boolean isNewAccount
) {
    public static OAuthCallbackResponse from(OAuthLoginResult result) {
        return new OAuthCallbackResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.refreshExpiresIn(),
                result.isNewAccount()
        );
    }
}
