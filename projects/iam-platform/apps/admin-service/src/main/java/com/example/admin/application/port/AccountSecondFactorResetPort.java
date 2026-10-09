package com.example.admin.application.port;

import java.time.Instant;

/**
 * TASK-MONO-771 S6 (owner decision OD-6) — the account-plane second factor lives in auth-service
 * ({@code account_totp}); admin-service resets it through this port (admin-to-auth.md § POST
 * /internal/auth/accounts/{accountId}/second-factor/reset). Keeps the HTTP client out of the application layer.
 *
 * <p>Failures surface as {@link com.example.admin.application.exception.DownstreamFailureException} — a 4xx as its
 * {@link com.example.admin.application.exception.NonRetryableDownstreamException} subtype carrying the HTTP status
 * and the downstream {@code code} ({@code TOTP_NOT_ENROLLED} · {@code ACCOUNT_NOT_FOUND}), which the use case maps
 * onto the public contract. A circuit-open rejection is the resilience library's own exception.
 */
public interface AccountSecondFactorResetPort {

    /**
     * Deletes the account's enrolment (confirmed or pending).
     *
     * @return what was deleted — never {@code null}
     */
    ResetResult reset(String accountId, String operatorId, String reason, String idempotencyKey);

    /**
     * @param wasConfirmed {@code true} when a confirmed enrolment was removed, {@code false} when only the pending
     *                     row of an unfinished {@code /mfa/setup} was
     */
    record ResetResult(String accountId, Instant resetAt, boolean wasConfirmed) {}
}
