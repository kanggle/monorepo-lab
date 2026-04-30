package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.KnownDeviceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKnownDeviceStore implements KnownDeviceStore {

    private static final String PREFIX = "security:device:seen:";
    private static final Duration TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isKnown(String accountId, String fingerprint) {
        try {
            Boolean m = redisTemplate.opsForSet().isMember(PREFIX + accountId, fingerprint);
            return Boolean.TRUE.equals(m);
        } catch (Exception e) {
            log.warn("Redis device:seen SISMEMBER failed for accountId={}", accountId, e);
            // Graceful "judgment deferred" — return true so the rule does not fire
            // on Redis outage (false-positive prevention per spec).
            return true;
        }
    }

    @Override
    public void remember(String accountId, String fingerprint) {
        try {
            String key = PREFIX + accountId;
            redisTemplate.opsForSet().add(key, fingerprint);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Redis device:seen SADD failed for accountId={}", accountId, e);
        }
    }
}
