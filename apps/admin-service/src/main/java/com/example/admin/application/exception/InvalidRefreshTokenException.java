package com.example.admin.application.exception;

/**
 * TASK-BE-040 — refresh JWT failed signature, expiry, token_type, or registry
 * lookup. Maps to 401 INVALID_REFRESH_TOKEN. Distinct from
 * {@link RefreshTokenReuseDetectedException} which signals a rotated jti
 * being replayed.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
