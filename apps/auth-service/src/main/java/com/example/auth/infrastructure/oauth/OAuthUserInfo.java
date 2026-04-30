package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;

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
