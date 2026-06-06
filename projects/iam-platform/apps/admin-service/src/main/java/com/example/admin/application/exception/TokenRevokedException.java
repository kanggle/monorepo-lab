package com.example.admin.application.exception;

/**
 * TASK-BE-040 — operator access JWT's jti was found on the Redis blacklist
 * (post-logout). Maps to 401 TOKEN_REVOKED. Also raised on Redis blacklist
 * lookup failure (fail-closed per audit-heavy A10).
 */
public class TokenRevokedException extends RuntimeException {
    public TokenRevokedException(String message) {
        super(message);
    }
}
