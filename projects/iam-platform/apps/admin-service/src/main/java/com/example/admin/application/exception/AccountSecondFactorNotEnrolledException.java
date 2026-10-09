package com.example.admin.application.exception;

/**
 * TASK-MONO-771 S6 — the account named in {@code POST /api/admin/accounts/{accountId}/2fa/reset} has no
 * account-plane second factor to delete (auth-service answered {@code 404 TOTP_NOT_ENROLLED}). Same public code as
 * the operator break-glass {@link TotpNotEnrolledException} — 404 {@code TOTP_NOT_ENROLLED} (admin-api.md) — but its
 * own message: the parent's handler message talks about recovery-code regeneration.
 */
public class AccountSecondFactorNotEnrolledException extends TotpNotEnrolledException {
    public AccountSecondFactorNotEnrolledException(String message) {
        super(message);
    }
}
