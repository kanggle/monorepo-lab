package com.example.auth.domain.oauth;

/**
 * User information retrieved from an OAuth provider after code exchange.
 */
public record OAuthUserInfo(
        String providerUserId,
        String email,
        String name,
        OAuthProvider provider
) {
}
