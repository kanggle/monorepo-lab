package com.example.auth.application.exception;

/**
 * Thrown when an unsupported OAuth provider is requested.
 */
public class UnsupportedProviderException extends RuntimeException {

    public UnsupportedProviderException(String provider) {
        super("Unsupported OAuth provider: " + provider);
    }
}
