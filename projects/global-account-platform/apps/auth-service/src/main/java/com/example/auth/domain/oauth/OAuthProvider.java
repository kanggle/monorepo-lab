package com.example.auth.domain.oauth;

/**
 * Supported OAuth providers.
 */
public enum OAuthProvider {
    GOOGLE,
    KAKAO,
    MICROSOFT;

    /**
     * Parses a provider string (case-insensitive) into an OAuthProvider.
     *
     * @throws IllegalArgumentException if the provider is not supported
     */
    public static OAuthProvider from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provider must not be blank");
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported OAuth provider: " + value);
        }
    }

    /**
     * Returns the login method string used in event payloads.
     */
    public String loginMethod() {
        return "OAUTH_" + name();
    }
}
