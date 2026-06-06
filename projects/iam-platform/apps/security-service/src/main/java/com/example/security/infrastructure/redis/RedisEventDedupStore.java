package com.example.security.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventDedupStore {

    private static final String KEY_PREFIX = "security:event-dedup:";

    private final StringRedisTemplate redisTemplate;

    @Value("${security-service.dedup.redis-ttl-seconds:86400}")
    private long ttlSeconds;

    /**
     * Check if event was already processed using Redis fast path.
     * Returns true if the event was already seen (dedup hit).
     * Returns false if the event is new or Redis is unavailable (graceful degradation).
     */
    public boolean isDuplicate(String eventId) {
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + eventId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis dedup check failed for eventId={}, falling back to DB", eventId, e);
            return false;
        }
    }

    /**
     * Mark event as processed in Redis with TTL.
     */
    public void markProcessed(String eventId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + eventId, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis dedup mark failed for eventId={}", eventId, e);
        }
    }
}
