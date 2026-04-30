package com.example.auth.application.exception;

/**
 * Thrown when the OAuth provider does not return an email address
 * (e.g., Kakao user who did not consent to email sharing).
 */
public class OAuthEmailRequiredException extends RuntimeException {

    public OAuthEmailRequiredException() {
        super("Email is required for social login");
    }
}
