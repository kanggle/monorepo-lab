package com.example.auth.application.exception;

/**
 * Thrown when the OAuth state parameter is invalid, missing, or expired.
 */
public class InvalidOAuthStateException extends RuntimeException {

    public InvalidOAuthStateException() {
        super("Invalid or expired OAuth state");
    }
}
