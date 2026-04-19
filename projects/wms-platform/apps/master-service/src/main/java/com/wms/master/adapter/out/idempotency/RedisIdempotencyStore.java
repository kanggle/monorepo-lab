package com.wms.master.adapter.out.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.application.port.out.IdempotencyStore;
import com.wms.master.application.port.out.StoredResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link IdempotencyStore}.
 * <p>
 * Entry key: {@code master:idem:{storageKey}} with JSON-encoded
 * {@link StoredResponse}. Lock key: {@code master:idem:lock:{storageKey}}.
 * TTL is applied per operation; no renewal on read.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String ENTRY_PREFIX = "master:idem:";
    private static final String LOCK_PREFIX = "master:idem:lock:";

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
            // Spec: stored entry malformed → treat as cache miss; overwrite on success
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
