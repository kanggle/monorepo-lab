package com.example.auth.domain.service;

public interface OAuthProvider {

    String provider();

    String buildAuthorizationUrl(String state, String redirectUri);

    OAuthUserInfo fetchUserInfo(String code, String redirectUri);

    record OAuthUserInfo(String email, String name) {}
}
