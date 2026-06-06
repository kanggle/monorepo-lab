package com.example.admin.application.port;

import java.time.Duration;

/**
 * TASK-BE-040 — port over the operator JWT blacklist (logout invalidation).
 *
 * <p>Production adapter is Redis-backed
 * ({@code admin:jti:blacklist:{jti}}); fail-closed on infra outage so the
 * application layer treats {@code isBlacklisted} returning {@code true} on
 * underlying error as the safe answer (audit-heavy A10).
 */
public interface TokenBlacklistPort {

    /** Marks the given access-token jti as revoked for the supplied TTL. */
    void blacklist(String jti, Duration ttl);

    /**
     * @return {@code true} if the jti has been blacklisted; also returns
     *         {@code true} when the underlying store is unreachable
     *         (fail-closed).
     */
    boolean isBlacklisted(String jti);
}
