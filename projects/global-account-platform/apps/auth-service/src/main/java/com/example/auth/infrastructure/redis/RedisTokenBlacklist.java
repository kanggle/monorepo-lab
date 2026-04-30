package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed token blacklist.
 *
 * <p>TASK-BE-229: key pattern changed to {@code refresh:blacklist:{tenant_id}:{jti}}
 * per specs/services/auth-service/architecture.md §Redis key patterns.
 * This enables tenant-isolated blacklist management.
 *
 * <p>For legacy compatibility (tokens without tenant context), the legacy key
 * {@code refresh:blacklist:{jti}} is also checked on reads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {

    /** New pattern (TASK-BE-229): refresh:blacklist:{tenant_id}:{jti} */
    private static final String KEY_PREFIX = "refresh:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Builds the tenant-aware Redis key.
     * Pattern: {@code refresh:blacklist:{tenantId}:{jti}}
     */
    private String buildKey(String tenantId, String jti) {
        return KEY_PREFIX + tenantId + ":" + jti;
    }

    /**
     * Legacy key (pre-TASK-BE-229): refresh:blacklist:{jti}
     */
    private String buildLegacyKey(String jti) {
        return KEY_PREFIX + jti;
    }

    /**
     * Blacklists a token using the tenant-aware key pattern.
     *
     * @param tenantId the tenant_id from the refresh token
     * @param jti      the JTI to blacklist
     * @param ttlSeconds TTL in seconds
     */
    public void blacklist(String tenantId, String jti, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(buildKey(tenantId, jti), "1",
                    Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for token blacklist write: {}", e.getMessage());
        }
    }

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        // Legacy single-arg call — use fan-platform default.
        blacklist("fan-platform", jti, ttlSeconds);
    }

    /**
     * Checks if a token is blacklisted using the tenant-aware key.
     * Also falls back to the legacy key (tokens issued before TASK-BE-229).
     *
     * @param tenantId the tenant_id from the refresh token
     * @param jti      the JTI to check
     */
    public boolean isBlacklisted(String tenantId, String jti) {
        try {
            boolean newKey = Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(tenantId, jti)));
            if (newKey) {
                return true;
            }
            // fallback to legacy key pattern for tokens issued before TASK-BE-229
            return Boolean.TRUE.equals(redisTemplate.hasKey(buildLegacyKey(jti)));
        } catch (DataAccessException e) {
            // fail-closed: if Redis is unavailable, treat as blacklisted (deny refresh)
            log.warn("Redis unavailable for token blacklist check, fail-closed: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        // Legacy single-arg call — use fan-platform default.
        return isBlacklisted("fan-platform", jti);
    }
}
