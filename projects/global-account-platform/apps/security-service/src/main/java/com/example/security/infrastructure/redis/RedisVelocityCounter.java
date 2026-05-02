package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.VelocityCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link VelocityCounter}.
 *
 * <p>Key format: {@code security:velocity:{tenantId}:{accountId}:{windowSeconds}}.
 * Each tenant/account pair maintains a completely independent counter, so a burst
 * of failures for one tenant never contributes to another tenant's threshold.</p>
 *
 * <p>TASK-BE-248 Phase 1: the key scheme changed from
 * {@code security:velocity:{accountId}:{windowSeconds}} to include {@code tenantId}
 * as the second segment. Legacy keys are not actively removed — they expire via
 * their natural TTL (windowSeconds + 60 s). During the overlap window the detection
 * pipeline may under-count for accounts whose counters were accumulated under the
 * old key. This is acceptable (false-negative bias) and preferable to incorrectly
 * attributing cross-tenant counts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisVelocityCounter implements VelocityCounter {

    private static final String PREFIX = "security:velocity:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public long incrementAndGet(String tenantId, String accountId, int windowSeconds) {
        String key = key(tenantId, accountId, windowSeconds);
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            // Set TTL on first increment (best-effort; stray EXPIRE calls are cheap).
            if (value != null && value == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 60L));
            }
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Redis velocity INCR failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    @Override
    public long peek(String tenantId, String accountId, int windowSeconds) {
        try {
            String v = redisTemplate.opsForValue().get(key(tenantId, accountId, windowSeconds));
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Redis velocity GET failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    private static String key(String tenantId, String accountId, int window) {
        return PREFIX + tenantId + ":" + accountId + ":" + window;
    }
}
