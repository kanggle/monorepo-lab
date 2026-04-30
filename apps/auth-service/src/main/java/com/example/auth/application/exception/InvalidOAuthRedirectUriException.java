package com.example.auth.application.exception;

/**
 * Thrown when the redirect_uri supplied by the OAuth client does not match the
 * server-side allowlist for the provider. Prevents open-redirect and
 * authorization-code interception attacks per RFC 6749 §10.6 / RFC 9700 §4.1.
 */
public class InvalidOAuthRedirectUriException extends RuntimeException {

    public InvalidOAuthRedirectUriException() {
        super("redirect_uri is not in the allowlist for the requested OAuth provider");
    }
}
