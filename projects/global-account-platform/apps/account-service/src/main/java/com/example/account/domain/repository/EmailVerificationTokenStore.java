package com.example.account.domain.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Port interface for the short-lived email verification token store (backed by Redis).
 *
 * <p>An email verification token is a single-use, time-bound credential issued by
 * {@code POST /api/accounts/signup/resend-verification-email} and consumed by
 * {@code POST /api/accounts/signup/verify-email} (TASK-BE-114). Tokens are
 * intentionally opaque to the account-service domain — the store only maps
 * {@code token → accountId} until expiry or until the token is consumed.</p>
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>{@link #save(String, String, Duration)} is overwrite-on-write so a
 *       repeat resend cleanly replaces an outstanding token instead of leaking
 *       two valid tokens for the same account.</li>
 *   <li>{@link #findAccountId(String)} returns empty for missing or expired
 *       tokens (TTL handling is implementation-side).</li>
 *   <li>{@link #delete(String)} is the single-use enforcement primitive: the
 *       verify path must call it after a successful verification so the same
 *       token cannot be replayed.</li>
 *   <li>{@link #tryAcquireResendSlot(String, Duration)} returns {@code true}
 *       only when no resend marker existed (Redis SET-NX semantics). When the
 *       marker already exists the caller must surface a 429.</li>
 * </ul>
 *
 * <p>Modeled on {@code PasswordResetTokenStore} (TASK-BE-108) — the verify-email
 * flow shares the same single-use, TTL-bound, opaque-token design.</p>
 */
public interface EmailVerificationTokenStore {

    /**
     * Persists {@code token → accountId} with the given TTL. Overwrites any
     * existing entry for the same token.
     */
    void save(String token, String accountId, Duration ttl);

    /**
     * Resolves a verification token to the account it was issued for. Returns
     * empty when the token does not exist, has expired, or has already been
     * {@link #delete(String) deleted}.
     */
    Optional<String> findAccountId(String token);

    /**
     * Removes the token entry. Callers must invoke this after the token has
     * been successfully consumed to enforce single-use semantics.
     */
    void delete(String token);

    /**
     * Attempts to acquire a resend slot for the given account using SET-NX
     * semantics. Returns {@code true} on success (caller may proceed to send),
     * {@code false} when a marker already exists (caller must reject with 429).
     *
     * <p>Implementations should fail-open on Redis errors — the resend flow
     * prioritises availability over strict rate limiting (see TASK-BE-114
     * Edge Cases).</p>
     */
    boolean tryAcquireResendSlot(String accountId, Duration ttl);
}
