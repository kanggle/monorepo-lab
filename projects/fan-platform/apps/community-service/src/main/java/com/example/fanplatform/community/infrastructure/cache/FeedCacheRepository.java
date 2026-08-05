package com.example.fanplatform.community.infrastructure.cache;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.FeedItemSnapshot;
import com.example.fanplatform.community.application.port.out.FeedCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed cache for paginated feed slices. Best-effort: any failure is
 * logged and counted as {@code community_feed_cache_unavailable_total}; the
 * feed query then falls through to Postgres (fail-open per
 * {@code rules/traits/integration-heavy.md} I3).
 *
 * <p>Key shape: {@code feed:&lt;version&gt;:&lt;tenantId&gt;:&lt;accountId&gt;:&lt;page&gt;:&lt;size&gt;}.
 * The cached value is the JSON-serialized {@link PageResult}&lt;{@link FeedItemSnapshot}&gt;
 * built by the use case, so a hit can be returned with zero DB round-trips.
 *
 * <p><strong>Why the key carries a version (TASK-FAN-BE-046).</strong> The cached payload used
 * to be a rendered {@code FeedItemView}, whose {@code title}/{@code bodyPreview} were already
 * nulled out for whatever was locked when the entry was written. Reading those bytes back as a
 * {@link FeedItemSnapshot} would deserialize cleanly and be wrong in a quiet way — an entitled
 * reader would get {@code null} titles until the entry expired. Bumping {@link #KEY_VERSION}
 * makes pre-change entries unreachable instead of misread, so no deploy-time flush is needed.
 * {@code TASK-FAN-BE-019} previously had to leave exactly that flush as a manual ops note.
 *
 * <p><strong>Invalidation strategy</strong>: TTL-only expiry
 * ({@value #TTL_MINUTES} minutes), and it covers <em>freshness</em> only. New posts and
 * follow-graph changes are visible after at most this staleness window — see
 * {@code architecture.md} § Read Path. A cache-aware consumer of
 * {@code community.post.published} / {@code community.follow.changed} can do explicit DEL if
 * sub-minute freshness is ever required. <strong>Entitlement is not on that list</strong>: it
 * is not cached here at all, so it needs no invalidation — see {@code GetFeedUseCase}.
 */
@Slf4j
@Component
public class FeedCacheRepository implements FeedCache {

    private static final TypeReference<PageResult<FeedItemSnapshot>> FEED_PAGE_TYPE = new TypeReference<>() {
    };

    /**
     * Bump whenever the cached payload's shape changes. Exposed so the integration
     * test builds its expected key from this constant rather than re-typing the literal — a
     * hand-copied key would keep asserting the old shape after a bump and pass anyway.
     */
    public static final String KEY_VERSION = "v2";

    static final long TTL_MINUTES = 5;
    private static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Counter cacheUnavailable;
    private final Counter cacheHit;
    private final Counter cacheMiss;

    public FeedCacheRepository(StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheUnavailable = Counter.builder("community_feed_cache_unavailable_total")
                .description("Number of feed cache operations that failed (fail-open to DB).")
                .register(meterRegistry);
        this.cacheHit = Counter.builder("community_feed_cache_hits_total")
                .description("Number of feed cache read hits (zero DB round-trips).")
                .register(meterRegistry);
        this.cacheMiss = Counter.builder("community_feed_cache_misses_total")
                .description("Number of feed cache read misses (fall-through to DB).")
                .register(meterRegistry);
    }

    /**
     * Best-effort write of the full {@link PageResult}&lt;{@link FeedItemSnapshot}&gt;
     * payload. Failures are logged + counted; the caller's response is
     * unaffected.
     */
    public void cachePage(String tenantId, String accountId, int page, int size, PageResult<FeedItemSnapshot> value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key(tenantId, accountId, page, size), json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("feed cache serialize failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
        } catch (RuntimeException e) {
            cacheUnavailable.increment();
            log.warn("feed cache write failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
        }
    }

    /**
     * Best-effort read of a previously cached {@link PageResult}&lt;{@link FeedItemSnapshot}&gt;.
     * Returns {@link Optional#empty()} on miss, deserialization error, or Redis
     * unavailability — the caller is expected to fall through to the DB.
     */
    public Optional<PageResult<FeedItemSnapshot>> readPage(String tenantId, String accountId, int page, int size) {
        try {
            String value = redis.opsForValue().get(key(tenantId, accountId, page, size));
            if (value == null || value.isEmpty()) {
                cacheMiss.increment();
                return Optional.empty();
            }
            PageResult<FeedItemSnapshot> feedPage = objectMapper.readValue(value, FEED_PAGE_TYPE);
            cacheHit.increment();
            return Optional.of(feedPage);
        } catch (JsonProcessingException e) {
            // Treat malformed cache entries as a miss (and increment unavailable
            // — they are functionally a degraded path).
            cacheUnavailable.increment();
            log.warn("feed cache deserialize failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            cacheUnavailable.increment();
            log.warn("feed cache read failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
            return Optional.empty();
        }
    }

    public static String key(String tenantId, String accountId, int page, int size) {
        return "feed:" + KEY_VERSION + ":" + tenantId + ":" + accountId + ":" + page + ":" + size;
    }
}
