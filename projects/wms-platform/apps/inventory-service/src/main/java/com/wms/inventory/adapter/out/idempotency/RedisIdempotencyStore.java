package com.wms.inventory.adapter.out.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.IdempotencyStore;
import com.wms.inventory.application.port.out.StoredResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link IdempotencyStore}.
 *
 * <p>Authoritative key shape (see {@code idempotency.md} §1.3):
 * {@code inventory:idempotency:{method}:{path_hash}:{idempotency_key}}.
 *
 * <h2>Prefix policy: <b>adapter prefixes</b></h2>
 *
 * <p>Callers (the future {@code IdempotencyFilter} from TASK-BE-022) pass the
 * <em>suffix</em> portion {@code {method}:{path_hash}:{idempotency_key}} as the
 * {@code storageKey} argument. This adapter prepends the canonical
 * {@code inventory:idempotency:} prefix on every read/write so the on-disk key
 * shape always matches the spec — without forcing every caller to remember it.
 *
 * <p>Lock keys live under a sibling prefix
 * {@code inventory:idempotency:lock:{method}:{path_hash}:{idempotency_key}} so
 * the entry and lock for the same request collocate in Redis log output but
 * never collide.
 *
 * <p>TTL is applied per operation; no renewal on read.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    /** Canonical prefix for the cached-response entry. Matches {@code idempotency.md:49}. */
    static final String ENTRY_PREFIX = "inventory:idempotency:";

    /**
     * Lock-key prefix. Sits under the same {@code inventory:idempotency:}
     * namespace but with a {@code lock:} segment to avoid collision with entry
     * keys (since {@code {method}} is uppercase {@code POST} / {@code PATCH},
     * never the literal {@code lock}).
     */
    static final String LOCK_PREFIX = "inventory:idempotency:lock:";

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
