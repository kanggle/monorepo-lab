package com.wms.admin.infra.idempotency;

import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.StoredResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link IdempotencyStore}.
 * <p>
 * Entry key: {@code admin:idem:{storageKey}} with JSON-encoded
 * {@link StoredResponse}. Lock key: {@code admin:idem:lock:{storageKey}}.
 * 24h TTL applied at write time.
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String ENTRY_PREFIX = "admin:idem:";
    private static final String LOCK_PREFIX = "admin:idem:lock:";

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
