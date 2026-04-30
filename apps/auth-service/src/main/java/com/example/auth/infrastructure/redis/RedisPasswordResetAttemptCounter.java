package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.PasswordResetAttemptCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed implementation of {@link PasswordResetAttemptCounter}.
 *
 * <p>Key format: {@code pwd-reset-rate:{emailHash}} — emailHash is the
 * 10-character SHA-256 truncation already established by
 * {@code LoginUseCase.hashEmail} (no PII stored as a Redis key).
 *
 * <p>{@link #tryAcquire(String)} performs an INCR; the first INCR sets a TTL
 * equal to the configured window. When the count exceeds the threshold the
 * caller is instructed to drop the request silently — see
 * {@code RequestPasswordResetUseCase} for the swallow logic that preserves the
 * existing "no-account-existence-leak" 204 response.
 *
 * <p>Read failures fail-open by returning {@code true} — consistent with
 * {@code RedisLoginAttemptCounter}. Documented in security review M-1.
 */
@Slf4j
@Component
public class RedisPasswordResetAttemptCounter implements PasswordResetAttemptCounter {

    private static final String KEY_PREFIX = "pwd-reset-rate:";

    private final StringRedisTemplate redisTemplate;
    private final long windowSeconds;
    private final int maxAttempts;

    public RedisPasswordResetAttemptCounter(
            StringRedisTemplate redisTemplate,
            @Value("${auth.password-reset.rate-limit.window-seconds:900}") long windowSeconds,
            @Value("${auth.password-reset.rate-limit.max-attempts:3}") int maxAttempts) {
        this.redisTemplate = redisTemplate;
        this.windowSeconds = windowSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean tryAcquire(String emailHash) {
        try {
            String key = KEY_PREFIX + emailHash;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                // Treat unexpected null as fail-open
                return true;
            }
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count <= maxAttempts;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for password-reset rate counter: {}", e.getMessage());
            return true;
        }
    }
}
