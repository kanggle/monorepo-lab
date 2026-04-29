package com.wms.outbound.adapter.out.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.port.out.IdempotencyStore;
import com.wms.outbound.application.port.out.StoredResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link IdempotencyStore}.
 *
 * <p>Authoritative key shape (see {@code idempotency.md} §1.3):
 * {@code outbound:idempotency:{method}:{path_hash}:{idempotency_key}}.
 *
 * <h2>Prefix policy: <b>adapter prefixes</b></h2>
 *
 * <p>Callers pass the <em>suffix</em> portion
 * {@code {method}:{path_hash}:{idempotency_key}} as the {@code storageKey}
 * argument. This adapter prepends the canonical {@code outbound:idempotency:}
 * prefix on every read/write so the on-disk key shape always matches the spec.
 *
 * <p>Lock keys live under a sibling prefix
 * {@code outbound:idempotency:lock:{method}:{path_hash}:{idempotency_key}} so
 * the entry and lock for the same request collocate in Redis log output but
 * never collide.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    /** Canonical prefix for the cached-response entry. Matches {@code idempotency.md} §1.3. */
    public static final String ENTRY_PREFIX = "outbound:idempotency:";

    /** Lock-key prefix. */
    public static final String LOCK_PREFIX = "outbound:idempotency:lock:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredResponse> lookup(String storageKey) {
        String raw = redis.opsForValue().get(ENTRY_PREFIX + storageKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, StoredResponse.class));
        } catch (JsonProcessingException e) {
            // Malformed entry — treat as cache miss; overwrite on success.
            return Optional.empty();
        }
    }

    @Override
    public void put(String storageKey, StoredResponse response, Duration ttl) {
        try {
            String raw = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(ENTRY_PREFIX + storageKey, raw, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency entry", e);
        }
    }

    @Override
    public boolean tryAcquireLock(String storageKey, Duration ttl) {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_PREFIX + storageKey, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseLock(String storageKey) {
        redis.delete(LOCK_PREFIX + storageKey);
    }
}
