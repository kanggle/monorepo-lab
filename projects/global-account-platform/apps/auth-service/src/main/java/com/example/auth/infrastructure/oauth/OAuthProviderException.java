package com.example.auth.infrastructure.oauth;

/**
 * Thrown when an OAuth provider call fails (token exchange, user info retrieval, etc.).
 */
public class OAuthProviderException extends RuntimeException {

    public OAuthProviderException(String message) {
        super(message);
    }

    public OAuthProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
