package com.example.security.jwt;

/**
 * Thrown when JWT verification fails due to invalid signature,
 * expired token, or other validation errors.
 */
public class JwtVerificationException extends RuntimeException {

    public JwtVerificationException(String message) {
        super(message);
    }

    public JwtVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
