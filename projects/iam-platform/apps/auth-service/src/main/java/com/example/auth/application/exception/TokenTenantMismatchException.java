package com.example.auth.application.exception;

/**
 * Thrown when a refresh token's tenant_id does not match the expected tenant_id during rotation.
 * Maps to HTTP 403 TOKEN_TENANT_MISMATCH (TASK-MONO-462: the token is valid and authenticated,
 * just not authorized to rotate into a different tenant — Forbidden, not Unauthorized).
 *
 * <p>Per specs/features/multi-tenancy.md §Refresh Token: cross-tenant refresh is absolutely
 * forbidden. A mismatch indicates tampering or a serious bug and triggers a security event.
 *
 * <p>Note: the SAS OAuth2 token-endpoint refresh grant ({@code SasRefreshTokenAuthenticationProvider})
 * surfaces the same semantic condition as {@code 400 invalid_grant} per RFC 6749 §5.2 — that is
 * deliberate divergence (a different endpoint, constrained by the OAuth2 spec's error vocabulary),
 * not a bug to unify with this 403.
 */
public class TokenTenantMismatchException extends RuntimeException {

    public TokenTenantMismatchException(String submittedTenantId, String expectedTenantId) {
        super("Token tenant_id mismatch: submitted=" + submittedTenantId
                + " expected=" + expectedTenantId);
    }
}
