package com.example.auth.presentation.dto;

import com.example.auth.application.result.OAuthAuthorizeResult;

public record OAuthAuthorizeResponse(
        String authorizationUrl,
        String state
) {
    public static OAuthAuthorizeResponse from(OAuthAuthorizeResult result) {
        return new OAuthAuthorizeResponse(result.authorizationUrl(), result.state());
    }
}
