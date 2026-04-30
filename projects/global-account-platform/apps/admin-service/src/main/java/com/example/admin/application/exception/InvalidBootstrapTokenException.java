package com.example.admin.application.exception;

/**
 * Thrown when a bootstrap token is missing, malformed, signed with an
 * unknown kid, past expiry, has the wrong {@code token_type}, or its
 * {@code jti} has already been consumed (Redis SETNX collision).
 * Mapped to 401 INVALID_BOOTSTRAP_TOKEN by {@code AdminExceptionHandler}.
 */
public class InvalidBootstrapTokenException extends RuntimeException {
    public InvalidBootstrapTokenException(String message) {
        super(message);
    }

    public InvalidBootstrapTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
