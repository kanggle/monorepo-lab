package com.example.auth.application.port;

import com.example.auth.domain.oauth.OAuthUserInfo;

/**
 * Interface for OAuth provider HTTP clients.
 * Each provider (Google, Kakao) implements this to exchange authorization codes
 * for user information.
 */
public interface OAuthClient {

    /**
     * Exchanges an authorization code for user information from the OAuth provider.
     *
     * @param code        the authorization code received from the provider callback
     * @param redirectUri the redirect URI used in the original authorization request
     * @return user info from the provider
     * @throws com.example.auth.application.exception.OAuthProviderException if the provider call fails
     */
    OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri);
}
