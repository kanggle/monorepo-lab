package com.example.account.infrastructure.redis;

import com.example.account.domain.repository.EmailVerificationTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link EmailVerificationTokenStore} (TASK-BE-114).
 *
 * <p>Storage layout:
 * <pre>
 *   token entry:   "email-verify:{token}"           (UUID v4 from the use case layer)
 *                   value: "{accountId}"
 *                   TTL:   24h (non-blocking signup spec)
 *
 *   resend marker: "email-verify:rate:{accountId}"  (SET-NX semantics)
 *                   value: "1"
 *                   TTL:   300s (5 minutes)
 * </pre>
 *
 * <p>Token reads/writes propagate Redis exceptions to the caller — verification
 * is security-sensitive so we do not silently drop a token write that the user
 * is waiting on. The use case layer surfaces a 503 in that case.</p>
 *
 * <p>The resend rate-limit slot is intentionally <strong>fail-open</strong>: if
 * Redis is unreachable we return {@code true} so the user can still receive
 * their verification email. The protection is best-effort throttling, not a
 * security boundary, and account availability matters more than an exact
 * 5-minute window during a Redis outage. See TASK-BE-114 Edge Cases.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEmailVerificationTokenStore implements EmailVerificationTokenStore {

    private static final String TOKEN_KEY_PREFIX = "email-verify:";
    private static final String RATE_KEY_PREFIX = "email-verify:rate:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, String accountId, Duration ttl) {
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, accountId, ttl);
    }

    @Override
    public Optional<String> findAccountId(String token) {
        String accountId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        return Optional.ofNullable(accountId);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
    }

    @Override
    public boolean tryAcquireResendSlot(String accountId, Duration ttl) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(RATE_KEY_PREFIX + accountId, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException e) {
            // Fail-open: availability over strict rate limiting (TASK-BE-114
            // Edge Cases). Account-id is internal, not PII — safe to log.
            log.warn("Resend rate-limit slot acquisition failed (Redis unavailable); "
                            + "allowing send for accountId={}: {}",
                    accountId, e.getMessage());
            return true;
        }
    }
}
