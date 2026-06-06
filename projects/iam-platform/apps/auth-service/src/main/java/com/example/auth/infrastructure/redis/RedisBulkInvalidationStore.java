package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.BulkInvalidationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-backed {@link BulkInvalidationStore}. Stores the marker timestamp (epoch-millis) so that
 * downstream consumers can enforce "tokens issued before X are invalid".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBulkInvalidationStore implements BulkInvalidationStore {

    private static final String KEY_PREFIX = "refresh:invalidate-all:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void invalidateAll(String accountId, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + accountId,
                    Long.toString(Instant.now().toEpochMilli()),
                    Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while setting bulk invalidation marker for account={}: {}",
                    accountId, e.getMessage());
        }
    }

    @Override
    public boolean isInvalidated(String accountId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + accountId));
        } catch (DataAccessException e) {
            // fail-closed: DB revoked flag is still the authoritative defence-in-depth guard
            log.warn("Redis unavailable while checking bulk invalidation marker, fail-closed: {}",
                    e.getMessage());
            return true;
        }
    }

    @Override
    public Optional<Instant> getInvalidatedAt(String accountId) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + accountId);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (NumberFormatException e) {
            log.warn("Malformed bulk invalidation marker for account={}, fail-closed: {}",
                    accountId, e.getMessage());
            return Optional.of(Instant.now());
        } catch (DataAccessException e) {
            // fail-closed: treat as invalidated now to reject all in-flight tokens
            log.warn("Redis unavailable while reading bulk invalidation marker, fail-closed: {}",
                    e.getMessage());
            return Optional.of(Instant.now());
        }
    }
}
