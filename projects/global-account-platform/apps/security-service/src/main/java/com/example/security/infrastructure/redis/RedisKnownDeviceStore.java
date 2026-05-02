package com.example.security.infrastructure.redis;

import com.example.security.domain.detection.KnownDeviceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link KnownDeviceStore}.
 *
 * <p>Key format: {@code security:device:known:{tenantId}:{accountId}}.
 * Each tenant/account pair maintains a completely independent known-device set, so a
 * device registered for one tenant never suppresses the DEVICE_CHANGE alert for another tenant.</p>
 *
 * <p>TASK-BE-248 Phase 2a: the key scheme changed from
 * {@code security:device:seen:{accountId}} to include {@code tenantId}.
 * Legacy keys are not actively removed — they expire after the TTL (90 days).
 * During the transition window DeviceChangeRule will return false-negatives for
 * accounts whose devices were stored under the old key. This is acceptable
 * (safe bias) and preferable to cross-tenant device leakage.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisKnownDeviceStore implements KnownDeviceStore {

    private static final String PREFIX = "security:device:known:";
    private static final Duration TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isKnown(String tenantId, String accountId, String fingerprint) {
        try {
            Boolean m = redisTemplate.opsForSet().isMember(key(tenantId, accountId), fingerprint);
            return Boolean.TRUE.equals(m);
        } catch (Exception e) {
            log.warn("Redis device:known SISMEMBER failed for tenantId={}, accountId={}", tenantId, accountId, e);
            // Graceful "judgment deferred" — return true so the rule does not fire
            // on Redis outage (false-positive prevention per spec).
            return true;
        }
    }

    @Override
    public void remember(String tenantId, String accountId, String fingerprint) {
        try {
            String k = key(tenantId, accountId);
            redisTemplate.opsForSet().add(k, fingerprint);
            redisTemplate.expire(k, TTL);
        } catch (Exception e) {
            log.warn("Redis device:known SADD failed for tenantId={}, accountId={}", tenantId, accountId, e);
        }
    }

    private static String key(String tenantId, String accountId) {
        return PREFIX + tenantId + ":" + accountId;
    }
}
