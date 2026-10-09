package com.example.admin.application.exception;

/**
 * TASK-MONO-771 — a read needed to decide whether a second factor is required (role flag, tenant entry
 * policy, assignment rows — all local admin_db reads) failed. Fail-closed: nothing is issued.
 *
 * <p>Maps to {@code 500 INTERNAL_ERROR} (admin-api.md § token-exchange; auth-to-admin.md rule 6 — a 5xx
 * on the internal check, which auth-service turns into a fail-closed deny). 🔴 Never filled in as
 * {@code 401} («not an operator») or {@code 403 MFA_REQUIRED} («step up»), and never as «not required»:
 * an unfinished decision must not look like either answer.
 */
public class SecondFactorRequirementUnavailableException extends RuntimeException {
    public SecondFactorRequirementUnavailableException(Throwable cause) {
        super("Second-factor requirement could not be determined", cause);
    }
}
