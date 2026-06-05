package com.example.auth.application.exception;

/**
 * Thrown when a refresh token's tenant_id does not match the expected tenant_id during rotation.
 * Maps to HTTP 401 TOKEN_TENANT_MISMATCH.
 *
 * <p>Per specs/features/multi-tenancy.md §Refresh Token: cross-tenant refresh is absolutely
 * forbidden. A mismatch indicates tampering or a serious bug and triggers a security event.
 */
public class TokenTenantMismatchException extends RuntimeException {

    public TokenTenantMismatchException(String submittedTenantId, String expectedTenantId) {
        super("Token tenant_id mismatch: submitted=" + submittedTenantId
                + " expected=" + expectedTenantId);
    }
}
