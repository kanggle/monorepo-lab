package com.example.auth.presentation.dto;

import com.example.auth.application.result.LoginResult;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.tokenType()
        );
    }
}
