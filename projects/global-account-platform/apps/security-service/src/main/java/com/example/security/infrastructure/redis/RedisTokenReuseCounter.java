package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.TokenReuseCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link TokenReuseCounter}.
 *
 * <p>Key format: {@code reuse:{tenantId}:{accountId}} with a 1-hour TTL. Each
 * tenant/account pair maintains a completely independent counter, so a burst
 * of reuse events for one tenant never contributes to another tenant's
 * counter or alerting threshold.</p>
 *
 * <p>TASK-BE-259: introduces the per-tenant key scheme. The previous global
 * scheme ({@code reuse:{accountId}}) is replaced outright; any legacy keys
 * expire naturally at TTL (1 hour). False-negative bias during the transient
 * overlap window is acceptable — token reuse always fires at score 100
 * regardless of the counter value, so the counter is observability-only.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenReuseCounter implements TokenReuseCounter {

    private static final String PREFIX = "reuse:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    public long incrementAndGet(String tenantId, String accountId) {
        String key = key(tenantId, accountId);
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            // Set TTL on first increment (best-effort; stray EXPIRE calls are cheap).
            if (value != null && value == 1L) {
                redisTemplate.expire(key, TTL);
            }
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Redis token-reuse INCR failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    @Override
    public long peek(String tenantId, String accountId) {
        try {
            String v = redisTemplate.opsForValue().get(key(tenantId, accountId));
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Redis token-reuse GET failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    private static String key(String tenantId, String accountId) {
        return PREFIX + tenantId + ":" + accountId;
    }
}
