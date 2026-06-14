package com.example.admin.application.exception;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — raised by the link operation when the target
 * account has NO resolvable central identity (the step-3b resolve EP returned a
 * {@code null} identityId — the account does not exist in the tenant, or has no
 * identity row yet).
 *
 * <p>This is the FAIL-CLOSED branch of the link authorization decision: with no
 * identity to link to, the link cannot proceed (opposite of the issuance
 * fail-soft). A downstream <em>failure</em> (account-service unavailable/errors)
 * is a {@code DownstreamFailureException} → 503; this exception is the
 * <em>successful resolve that yielded null</em>. Surfaces as
 * {@code 422 ACCOUNT_IDENTITY_UNRESOLVABLE}.
 */
public class AccountIdentityUnresolvableException extends RuntimeException {
    public AccountIdentityUnresolvableException(String message) {
        super(message);
    }
}
