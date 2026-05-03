package com.example.fanplatform.community.infrastructure.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed cache for paginated feed slices. Best-effort: any failure is
 * logged and counted as {@code community_feed_cache_unavailable_total}; the
 * feed query then falls through to Postgres (fail-open per
 * {@code rules/traits/integration-heavy.md} I3).
 *
 * <p>Key shape: {@code feed:fan-platform:<accountId>:<page>:<size>}.
 * Stored as a JSON string list of post ids; v2 will likely move to a Redis
 * Sorted Set with score=published_at for cursor-style paging.
 */
@Slf4j
@Component
public class FeedCacheRepository {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String DELIM = "|";

    private final StringRedisTemplate redis;
    private final Counter cacheUnavailable;

    public FeedCacheRepository(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.cacheUnavailable = Counter.builder("community_feed_cache_unavailable_total")
                .description("Number of feed cache operations that failed (fail-open to DB).")
                .register(meterRegistry);
    }

    public void cachePage(String tenantId, String accountId, int page, int size, List<String> postIds) {
        try {
            redis.opsForValue().set(key(tenantId, accountId, page, size),
                    String.join(DELIM, postIds), TTL);
        } catch (RuntimeException e) {
            cacheUnavailable.increment();
            log.warn("feed cache write failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
        }
    }

    public List<String> readPage(String tenantId, String accountId, int page, int size) {
        try {
            String value = redis.opsForValue().get(key(tenantId, accountId, page, size));
            if (value == null || value.isEmpty()) {
                return List.of();
            }
            return List.of(value.split("\\" + DELIM));
        } catch (RuntimeException e) {
            cacheUnavailable.increment();
            log.warn("feed cache read failed: tenant={} account={} page={} size={} reason={}",
                    tenantId, accountId, page, size, e.getMessage());
            return List.of();
        }
    }

    private static String key(String tenantId, String accountId, int page, int size) {
        return "feed:" + tenantId + ":" + accountId + ":" + page + ":" + size;
    }
}
