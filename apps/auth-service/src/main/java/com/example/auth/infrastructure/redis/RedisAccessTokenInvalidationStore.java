package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed {@link AccessTokenInvalidationStore} (TASK-BE-146). Writes the
 * marker key the gateway's {@code JwtAuthenticationFilter} reads
 * ({@code access:invalidate-before:{accountId}}) so any access token whose
 * {@code iat} ≤ the stored epoch-millis is rejected for one access-token TTL.
 *
 * <p>Fail-soft: a {@link DataAccessException} from Redis is logged and
 * swallowed so a transient outage does not block the password reset path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAccessTokenInvalidationStore implements AccessTokenInvalidationStore {

    static final String KEY_PREFIX = "access:invalidate-before:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void invalidateAccessBefore(String accountId, Instant at, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + accountId,
                    Long.toString(at.toEpochMilli()),
                    Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while setting access invalidation marker for account={}: {}",
                    accountId, e.getMessage());
        }
    }
}
