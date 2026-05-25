package com.example.fanplatform.community.application.port.out;

import com.example.fanplatform.community.application.FeedPage;

import java.util.Optional;

/**
 * Output port for the follow-based feed cache. Implementations are
 * infrastructure-layer components (e.g. the Redis-backed
 * {@code FeedCacheRepository}); the application layer references only this
 * interface (Layered Architecture boundary rule — {@code application/} MUST NOT
 * import {@code infrastructure/}).
 *
 * <p>All operations are best-effort / fail-open: implementations MUST NOT
 * throw on cache unavailability — they must return {@link Optional#empty()}
 * (reads) or silently swallow the error (writes), per
 * {@code rules/traits/integration-heavy.md} I3.
 */
public interface FeedCache {

    /**
     * Attempts to read a previously cached feed page. Returns
     * {@link Optional#empty()} on miss, deserialization error, or
     * infrastructure unavailability.
     */
    Optional<FeedPage> readPage(String tenantId, String accountId, int page, int size);

    /**
     * Best-effort write of the hydrated feed page. The caller's response is
     * unaffected by write failures.
     */
    void cachePage(String tenantId, String accountId, int page, int size, FeedPage value);
}
