package com.example.fanplatform.community.infrastructure.cache;

import com.example.fanplatform.community.application.FeedPage;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * <p>Key shape: {@code feed:&lt;tenantId&gt;:&lt;accountId&gt;:&lt;page&gt;:&lt;size&gt;}.
 * The cached value is the JSON-serialized {@link FeedPage} returned by the
 * use case, so a hit can be returned with zero DB round-trips.
 *
 * <p><strong>Invalidation strategy</strong>: v1 uses TTL-only expiry
 * ({@value #TTL_MINUTES} minutes). New posts and follow-graph changes are
 * therefore visible after at most this staleness window — see
 * {@code architecture.md} § Read Path. A v2 cache-aware consumer of
 * {@code community.post.published} / {@code community.follow.changed} can do
 * explicit DEL when sub-minute freshness is required.
 */
@Slf4j
@Component
public class FeedCacheRepository {

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
     * Best-effort write of the full {@link FeedPage} payload. Failures are
     * logged + counted; the caller's response is unaffected.
     */
    public void cachePage(String tenantId, String accountId, int page, int size, FeedPage value) {
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
     * Best-effort read of a previously cached {@link FeedPage}. Returns
     * {@link Optional#empty()} on miss, deserialization error, or Redis
     * unavailability — the caller is expected to fall through to the DB.
     */
    public Optional<FeedPage> readPage(String tenantId, String accountId, int page, int size) {
        try {
            String value = redis.opsForValue().get(key(tenantId, accountId, page, size));
            if (value == null || value.isEmpty()) {
                cacheMiss.increment();
                return Optional.empty();
            }
            FeedPage feedPage = objectMapper.readValue(value, FeedPage.class);
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

    private static String key(String tenantId, String accountId, int page, int size) {
        return "feed:" + tenantId + ":" + accountId + ":" + page + ":" + size;
    }
}
