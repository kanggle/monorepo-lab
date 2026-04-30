package com.example.auth.infrastructure.oauth;

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
     * @throws OAuthProviderException if the provider call fails
     */
    OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri);
}
