package com.example.auth.application.exception;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — the assume-tenant assignment
 * gate denied issuance. Thrown by the assume-tenant authentication provider when
 * the admin-service assignment check does NOT return {@code assigned=true}.
 *
 * <p><b>fail-CLOSED</b>: this covers BOTH "not assigned" AND "admin-service
 * unavailable" (timeout / 4xx / 5xx / circuit-open / IO). The assignment check
 * is an authorization gate, so any uncertainty denies the token — the opposite
 * of the account-service {@code entitled_domains} fail-soft policy. Mapped to RFC
 * 8693 {@code invalid_grant} (HTTP 400) by the provider; no token is minted.
 */
public class AssumeTenantDeniedException extends RuntimeException {

    public AssumeTenantDeniedException(String message) {
        super(message);
    }

    public AssumeTenantDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
