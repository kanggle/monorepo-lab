package com.example.auth.domain.repository;

import java.time.Instant;

/**
 * Port for the per-account access-token invalidation marker (TASK-BE-146).
 *
 * <p>Whereas {@link BulkInvalidationStore} guards refresh-token rotation paths,
 * this marker is consumed by the gateway's {@code JwtAuthenticationFilter} to
 * reject any access token whose {@code iat} precedes the marker timestamp.
 *
 * <p>The marker key + TTL must align with the gateway's read path
 * ({@code access:invalidate-before:{accountId}}). TTL must be at least as
 * long as the access-token TTL — otherwise the marker can expire while a
 * pre-reset access token is still presentable, defeating the guarantee
 * (TASK-BE-146 Failure Scenarios).
 * Implementations are responsible for fail-soft semantics — a Redis outage
 * MUST NOT propagate to the caller (the password reset itself has already
 * committed a new credential hash by the time this is called; refusing to
 * complete would lock the user out).
 */
public interface AccessTokenInvalidationStore {

    /**
     * Records that all access tokens for {@code accountId} issued at or before
     * {@code at} should be rejected by the gateway, for {@code ttlSeconds}.
     * Always overwrites any existing marker so the most recent reset wins —
     * see TASK-BE-146 Edge Cases.
     */
    void invalidateAccessBefore(String accountId, Instant at, long ttlSeconds);
}
