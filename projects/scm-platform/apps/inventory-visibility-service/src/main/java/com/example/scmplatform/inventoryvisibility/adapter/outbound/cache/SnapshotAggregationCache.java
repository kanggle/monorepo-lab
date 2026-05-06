package com.example.scmplatform.inventoryvisibility.adapter.outbound.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis cache for cross-node SKU aggregation results (Acceptance Criteria #16).
 * <p>
 * TTL: 5 minutes (configurable via {@code inventory-visibility.cache.ttl-seconds}).
 * Fail-open: if Redis is down, returns empty (caller falls back to DB query).
 * Response header {@code X-Cache: HIT | MISS | UNAVAILABLE} is set by the controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotAggregationCache {

    private static final String KEY_PREFIX = "inv:agg:sku:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${inventory-visibility.cache.ttl-seconds:300}")
    private long ttlSeconds;

    public <T> Optional<T> get(String sku, String tenantId, TypeReference<T> typeRef) {
        String key = buildKey(sku, tenantId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.warn("Redis cache get failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public <T> void put(String sku, String tenantId, T value) {
        String key = buildKey(sku, tenantId);
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.warn("Redis cache serialize failed for key={}: {}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis cache put failed for key={}: {}", key, e.getMessage());
        }
    }

    public boolean isAvailable() {
        try {
            redisTemplate.opsForValue().get("__health_check__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildKey(String sku, String tenantId) {
        return KEY_PREFIX + tenantId + ":" + sku;
    }
}
