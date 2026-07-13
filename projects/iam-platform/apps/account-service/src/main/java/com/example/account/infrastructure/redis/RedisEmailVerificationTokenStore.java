package com.example.account.infrastructure.redis;

import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.tenant.TenantId;
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
 *                   value: "{tenantId}|{accountId}"  (TASK-BE-507)
 *                   TTL:   24h (non-blocking signup spec)
 *
 *   resend marker: "email-verify:rate:{accountId}"  (SET-NX semantics)
 *                   value: "1"
 *                   TTL:   300s (5 minutes)
 * </pre>
 *
 * <p>TASK-BE-507: the value gained a {@code {tenantId}|} prefix so the verify path can
 * scope its account lookup (it is token-authenticated — no {@code X-Tenant-Id} reaches
 * it). A value with no separator is a <b>pre-BE-507 token still inside its 24h TTL</b>
 * and resolves to {@code fan-platform} — the only tenant signup could produce before this
 * task, so the fallback is exact, not a guess. It can be dropped once a deploy is 24h old.
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
    /** Separator for the {@code {tenantId}|{accountId}} value. A tenant slug can never contain it. */
    private static final char VALUE_SEPARATOR = '|';

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, String tenantId, String accountId, Duration ttl) {
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, tenantId + VALUE_SEPARATOR + accountId, ttl);
    }

    @Override
    public Optional<Subject> findSubject(String token) {
        String raw = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (raw == null) {
            return Optional.empty();
        }
        int sep = raw.indexOf(VALUE_SEPARATOR);
        if (sep < 0) {
            // Pre-BE-507 token minted before the tenant prefix existed. Signup could only
            // produce fan-platform accounts back then, so this resolves exactly.
            return Optional.of(new Subject(TenantId.FAN_PLATFORM.value(), raw));
        }
        return Optional.of(new Subject(raw.substring(0, sep), raw.substring(sep + 1)));
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
