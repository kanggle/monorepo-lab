package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.PasswordResetTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link PasswordResetTokenStore}.
 *
 * <p>Storage layout:
 * <pre>
 *   key:   "pwd-reset:{token}"   (token is a UUID v4 from the use case layer)
 *   value: "{accountId}"
 *   TTL:   from caller (TASK-BE-108 spec: 1 hour)
 * </pre>
 *
 * <p>Reads and writes propagate Redis exceptions to the caller. Unlike the
 * login-failure counter we do <strong>not</strong> fail-open here: the reset
 * flow is security-critical, and silently dropping a token write would issue
 * a reset email that the user can never use to complete the flow. The
 * controller path returns 500 if Redis is down, which is the documented
 * failure scenario for TASK-BE-108.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String KEY_PREFIX = "pwd-reset:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, String accountId, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + token, accountId, ttl);
    }

    @Override
    public Optional<String> findAccountId(String token) {
        String accountId = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return Optional.ofNullable(accountId);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
