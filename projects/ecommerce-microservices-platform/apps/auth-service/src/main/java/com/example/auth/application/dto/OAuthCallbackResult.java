package com.example.auth.application.dto;

public record OAuthCallbackResult(boolean success, String callbackUrl, LoginResult loginResult) {

    public static OAuthCallbackResult success(String callbackUrl, LoginResult loginResult) {
        return new OAuthCallbackResult(true, callbackUrl, loginResult);
    }

    public static OAuthCallbackResult failure(String callbackUrl) {
        return new OAuthCallbackResult(false, callbackUrl, null);
    }
}
