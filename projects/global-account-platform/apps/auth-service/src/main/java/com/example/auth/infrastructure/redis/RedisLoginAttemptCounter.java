package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.LoginAttemptCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed login attempt counter.
 *
 * <p>TASK-BE-229: key pattern changed to {@code login:fail:{tenant_id}:{email_hash}}
 * per specs/services/auth-service/architecture.md §Redis key patterns.
 * This enables tenant-isolated rate limiting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLoginAttemptCounter implements LoginAttemptCounter {

    /** New pattern (TASK-BE-229): login:fail:{tenant_id}:{email_hash} */
    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redisTemplate;

    @Value("${auth.login.failure-window-seconds:900}")
    private long failureWindowSeconds;

    /**
     * Builds the tenant-aware Redis key.
     * Pattern: {@code login:fail:{tenantId}:{emailHash}}
     */
    private String buildKey(String tenantId, String emailHash) {
        return KEY_PREFIX + tenantId + ":" + emailHash;
    }

    @Override
    public int getFailureCount(String emailHash) {
        // Legacy single-key lookup — delegates to fan-platform default.
        return getFailureCount("fan-platform", emailHash);
    }

    /**
     * Returns the failure count for the given tenant and email hash.
     */
    public int getFailureCount(String tenantId, String emailHash) {
        try {
            String value = redisTemplate.opsForValue().get(buildKey(tenantId, emailHash));
            return value != null ? Integer.parseInt(value) : 0;
        } catch (DataAccessException e) {
            // Redis failure: fail-open for counter reads (allow login, warn via metrics)
            log.warn("Redis unavailable for login failure counter read: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void incrementFailureCount(String emailHash) {
        // Legacy — delegates to fan-platform default.
        incrementFailureCount("fan-platform", emailHash);
    }

    /**
     * Increments the failure counter for the given tenant and email hash.
     */
    public void incrementFailureCount(String tenantId, String emailHash) {
        try {
            String key = buildKey(tenantId, emailHash);
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofSeconds(failureWindowSeconds));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for login failure counter increment: {}", e.getMessage());
        }
    }

    @Override
    public void resetFailureCount(String emailHash) {
        // Legacy — delegates to fan-platform default.
        resetFailureCount("fan-platform", emailHash);
    }

    /**
     * Resets the failure counter for the given tenant and email hash.
     */
    public void resetFailureCount(String tenantId, String emailHash) {
        try {
            redisTemplate.delete(buildKey(tenantId, emailHash));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for login failure counter reset: {}", e.getMessage());
        }
    }
}
