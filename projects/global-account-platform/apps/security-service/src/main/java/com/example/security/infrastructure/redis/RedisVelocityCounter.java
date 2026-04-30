package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.VelocityCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisVelocityCounter implements VelocityCounter {

    private static final String PREFIX = "security:velocity:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public long incrementAndGet(String accountId, int windowSeconds) {
        String key = key(accountId, windowSeconds);
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            // Set TTL on first increment (best-effort; stray EXPIRE calls are cheap).
            if (value != null && value == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 60L));
            }
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Redis velocity INCR failed for accountId={}; returning 0", accountId, e);
            return 0L;
        }
    }

    @Override
    public long peek(String accountId, int windowSeconds) {
        try {
            String v = redisTemplate.opsForValue().get(key(accountId, windowSeconds));
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Redis velocity GET failed for accountId={}; returning 0", accountId, e);
            return 0L;
        }
    }

    private static String key(String accountId, int window) {
        return PREFIX + accountId + ":" + window;
    }
}
