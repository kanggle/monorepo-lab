package com.example.auth.application.exception;

/**
 * Thrown when a previously rotated refresh token is presented for rotation again.
 *
 * <p>Per the auth API contract this maps to HTTP 401 with error code
 * {@code TOKEN_REUSE_DETECTED}. All sessions for the account are revoked
 * as part of the handling path.
 */
public class TokenReuseDetectedException extends RuntimeException {

    public TokenReuseDetectedException() {
        super("Refresh token reuse detected");
    }
}
