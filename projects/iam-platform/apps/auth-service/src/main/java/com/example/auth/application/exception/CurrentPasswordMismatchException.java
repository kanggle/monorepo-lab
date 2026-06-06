package com.example.auth.application.exception;

/**
 * Thrown by the password-change flow when the supplied
 * {@code currentPassword} does not match the stored credential hash.
 *
 * <p>This is a subtype of {@link CredentialsInvalidException} so existing
 * "credentials invalid" tests/handlers continue to apply, but the
 * password-change endpoint maps it to HTTP 400 (per {@code auth-api.md}
 * {@code PATCH /api/auth/password}) instead of the 401 used by the login
 * endpoint. The carried message must never include the plaintext password
 * (R4 — rules/traits/regulated.md).</p>
 */
public class CurrentPasswordMismatchException extends CredentialsInvalidException {

    public CurrentPasswordMismatchException() {
        super();
    }
}
