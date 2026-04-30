package com.example.auth.domain.repository;

/**
 * Port interface for password-reset request rate limiting (backed by Redis).
 *
 * <p>Mirrors {@link LoginAttemptCounter} but is keyed on the SHA-256-truncated
 * email hash and gated by a separate window/threshold (TASK-BE-144). The counter
 * is incremented for every reset request, regardless of whether the email
 * resolves to an existing credential, to preserve the API's
 * "no-account-existence-leak" semantics.
 *
 * <p>Reads fail-open on Redis outage (counter returns 0) — consistent with
 * {@link LoginAttemptCounter}'s precedent. The trade-off is documented in the
 * security review M-1 finding; it is preserved here for behavioural parity
 * across rate-limited paths.
 */
public interface PasswordResetAttemptCounter {

    /**
     * Returns {@code true} when at least one slot remains in the current window
     * for the given email hash. Returns {@code true} on Redis outage (fail-open).
     */
    boolean tryAcquire(String emailHash);
}
