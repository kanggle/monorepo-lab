package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.domain.rbac.PermissionEvaluator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * Cache-Aside decorator over {@link PermissionEvaluatorImpl} using Redis.
 *
 * <p>Implements the caching policy declared in
 * {@code specs/services/admin-service/rbac.md} §Caching:
 * <ul>
 *   <li>Key: {@code admin:operator:perm:{operator_id}} (external UUID v7)</li>
 *   <li>TTL: 10 seconds</li>
 *   <li>Value: JSON array of permission key strings (Set&lt;String&gt;)</li>
 *   <li>Invalidation hook: {@link #invalidate(String)} is called by role-change
 *       / operator-deactivation paths.</li>
 * </ul>
 *
 * <p>Graceful degrade: when Redis is unreachable the decorator catches both
 * Spring's {@link DataAccessException} hierarchy (covers
 * {@code RedisConnectionFailureException}, {@code QueryTimeoutException}, and
 * Spring-translated Redis errors) and Lettuce's {@link RedisException}
 * hierarchy (covers {@code RedisCommandTimeoutException},
 * {@code RedisConnectionException}, and raw socket-level failures that bubble
 * up before Spring's translation layer), falls back to the origin DB path,
 * and logs at WARN — stale permission checks are preferable to total outage
 * (task Edge Cases). Non-Redis {@code RuntimeException}s are intentionally
 * allowed to propagate so real bugs are not silently swallowed.
 */
@Slf4j
@Primary
@Component
public class CachingPermissionEvaluator implements PermissionEvaluator {

    private static final TypeReference<Set<String>> SET_OF_STRING = new TypeReference<>() {};

    private final PermissionEvaluatorImpl origin;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final String keyPrefix;

    public CachingPermissionEvaluator(
            @Qualifier("originPermissionEvaluator") PermissionEvaluatorImpl origin,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${admin.permission-cache.ttl-seconds:10}") long ttlSeconds,
            @Value("${admin.permission-cache.key-prefix:admin:operator:perm:}") String keyPrefix) {
        this.origin = origin;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean hasPermission(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return false;
        }
        return resolve(operatorId).contains(permission);
    }

    @Override
    public boolean hasAllPermissions(String operatorId, Collection<String> permissions) {
        if (operatorId == null || permissions == null || permissions.isEmpty()) {
            return false;
        }
        return resolve(operatorId).containsAll(permissions);
    }

    /**
     * Remove the cached permission set for {@code operatorId}. Intended to be
     * invoked by role-change / operator-deactivation code paths; public method
     * is service-internal per task Scope (no admin API in this increment).
     */
    public void invalidate(String operatorId) {
        if (operatorId == null) return;
        try {
            redis.delete(cacheKey(operatorId));
        } catch (DataAccessException | RedisException ex) {
            log.warn("Redis unavailable during invalidate, skipping: operatorId={} cause={}",
                    operatorId, ex.getClass().getSimpleName());
        }
    }

    private Set<String> resolve(String operatorId) {
        String key = cacheKey(operatorId);

        // Read-through
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, SET_OF_STRING);
            }
        } catch (DataAccessException | RedisException ex) {
            log.warn("Redis unavailable on GET, degrading to DB: operatorId={} cause={}",
                    operatorId, ex.getClass().getSimpleName());
            return origin.loadPermissions(operatorId);
        } catch (JsonProcessingException ex) {
            log.warn("Malformed cache entry, evicting and recomputing: operatorId={}", operatorId, ex);
            try {
                redis.delete(key);
            } catch (RuntimeException ignore) {
                // best-effort
            }
        }

        // Miss → origin
        Set<String> perms = origin.loadPermissions(operatorId);

        // Write-through (best effort; degrade silently on Redis outage)
        try {
            String json = objectMapper.writeValueAsString(perms);
            redis.opsForValue().set(key, json, ttl);
        } catch (DataAccessException | RedisException ex) {
            log.warn("Redis unavailable on SET, continuing without cache: operatorId={} cause={}",
                    operatorId, ex.getClass().getSimpleName());
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize permission set, continuing without cache: operatorId={}",
                    operatorId, ex);
        }
        return perms;
    }

    private String cacheKey(String operatorId) {
        return keyPrefix + operatorId;
    }
}
