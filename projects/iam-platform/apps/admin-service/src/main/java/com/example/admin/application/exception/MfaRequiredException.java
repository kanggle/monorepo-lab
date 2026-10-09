package com.example.admin.application.exception;

/**
 * TASK-MONO-771 (ADR-MONO-080 D4 · R2, owner decisions OD-2 · OD-3) — the operator token exchange
 * resolved an ACTIVE operator, a second factor is required for it, and the subject token's {@code amr}
 * does not contain {@code "mfa"}. No operator token is minted.
 *
 * <p>🔴 Deliberately NOT a subclass of {@link OperatorUnauthorizedException}: that one maps to
 * {@code 401 TOKEN_INVALID}, which the console reads as «not an operator → /onboarding» (AC-0 F1). This
 * maps to {@code 403 MFA_REQUIRED} (admin-api.md § token-exchange), which the console reads as «step up».
 * The message never names which term (role or tenant policy) required it.
 */
public class MfaRequiredException extends RuntimeException {
    public MfaRequiredException() {
        super("A second authentication factor is required");
    }
}
