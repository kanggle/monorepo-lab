package com.example.auth.application.exception;

/**
 * Thrown when the same email exists in multiple tenants and no tenantId was specified on login.
 * Maps to HTTP 400 LOGIN_TENANT_AMBIGUOUS.
 *
 * <p>Per specs/features/multi-tenancy.md §Edge Cases: when no tenantId is provided and the email
 * matches multiple tenants, the platform requires explicit tenant selection from the caller.
 */
public class LoginTenantAmbiguousException extends RuntimeException {

    public LoginTenantAmbiguousException() {
        super("Email exists in multiple tenants; please specify tenantId");
    }
}
