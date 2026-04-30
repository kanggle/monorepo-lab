package com.example.auth.domain.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Port interface for the short-lived password reset token store (backed by Redis).
 *
 * <p>A reset token is a single-use, time-bound credential issued by
 * {@code POST /api/auth/password-reset/request} and consumed by
 * {@code POST /api/auth/password-reset/confirm} (TASK-BE-109). Tokens are
 * intentionally opaque to the auth-service domain — the store only maps
 * {@code token → accountId} until expiry or until the token is consumed.</p>
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>{@link #save(String, String, Duration)} is overwrite-on-write so a
 *       repeat request for the same account cleanly replaces an outstanding
 *       token instead of leaking two valid tokens.</li>
 *   <li>{@link #findAccountId(String)} returns empty for missing or expired
 *       tokens (TTL handling is implementation-side).</li>
 *   <li>{@link #delete(String)} is the single-use enforcement primitive: the
 *       confirm path must call it after a successful password change so the
 *       same token cannot be replayed.</li>
 * </ul>
 *
 * <p>Introduced by TASK-BE-108.</p>
 */
public interface PasswordResetTokenStore {

    /**
     * Persists {@code token → accountId} with the given TTL. Overwrites any
     * existing entry for the same token.
     */
    void save(String token, String accountId, Duration ttl);

    /**
     * Resolves a reset token to the account it was issued for. Returns empty
     * when the token does not exist, has expired, or has already been
     * {@link #delete(String) deleted}.
     */
    Optional<String> findAccountId(String token);

    /**
     * Removes the token entry. Callers must invoke this after the token has
     * been successfully consumed to enforce single-use semantics.
     */
    void delete(String token);
}
