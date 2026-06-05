package com.example.membership.infrastructure.redis;

import com.example.membership.application.idempotency.IdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed idempotency cache. Best-effort — a Redis outage must not break the
 * subscription write path, because final uniqueness is enforced by the DB unique
 * constraint on {@code (account_id, plan_level, status=ACTIVE)} and by the caller's
 * idempotency key when the subscription row is created. On cache failure we log at
 * WARN and degrade: {@code get} returns empty (treated as cache miss), {@code
 * putIfAbsent} returns {@code true} (treated as first write). This keeps the flow
 * fail-open for the optimisation while the DB remains the source of truth.
 */
@Slf4j
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "membership:idem:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotencyStore(StringRedisTemplate redis,
                                 @Value("${membership.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean putIfAbsent(String key, String subscriptionId) {
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + key, subscriptionId, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException e) {
            log.warn("Idempotency cache put failed, proceeding fail-open (key prefix={}): {}",
                    KEY_PREFIX, e.getMessage());
            return true;
        }
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + key));
        } catch (DataAccessException e) {
            log.warn("Idempotency cache get failed, treating as cache miss (key prefix={}): {}",
                    KEY_PREFIX, e.getMessage());
            return Optional.empty();
        }
    }
}
