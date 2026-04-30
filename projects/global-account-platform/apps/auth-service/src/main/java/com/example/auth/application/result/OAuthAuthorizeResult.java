package com.example.auth.application.result;

public record OAuthAuthorizeResult(
        String authorizationUrl,
        String state
) {
}
