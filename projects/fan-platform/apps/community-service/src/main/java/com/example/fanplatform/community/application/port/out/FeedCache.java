package com.example.fanplatform.community.application.port.out;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.FeedItemSnapshot;

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
 *
 * <p><strong>The unit is a {@link FeedItemSnapshot} page, never a rendered
 * {@code FeedItemView} page (TASK-FAN-BE-046).</strong> That is a property of this port, not
 * an implementation detail of one adapter: a cache that can hold a {@code locked} flag can
 * serve an authorization decision that has since stopped being true. Keeping the type
 * entitlement-free is what makes that unrepresentable, so a future adapter cannot reintroduce
 * it by accident.
 */
public interface FeedCache {

    /**
     * Attempts to read a previously cached feed page. Returns
     * {@link Optional#empty()} on miss, deserialization error, or
     * infrastructure unavailability.
     */
    Optional<PageResult<FeedItemSnapshot>> readPage(String tenantId, String accountId, int page, int size);

    /**
     * Best-effort write of the feed page projection. The caller's response is
     * unaffected by write failures.
     */
    void cachePage(String tenantId, String accountId, int page, int size, PageResult<FeedItemSnapshot> value);
}
