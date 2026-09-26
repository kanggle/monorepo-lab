package com.example.security.service.infrastructure.redis;

import com.example.security.service.domain.detection.TokenReuseCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed implementation of {@link TokenReuseCounter}.
 *
 * <p>Key format: {@code reuse:{tenantId}:{accountId}} with a 1-hour TTL. Each
 * tenant/account pair maintains a completely independent counter, so a burst
 * of reuse events for one tenant never contributes to another tenant's
 * counter or alerting threshold.</p>
 *
 * <p>TASK-BE-259: introduces the per-tenant key scheme. The previous global
 * scheme ({@code reuse:{accountId}}) is replaced outright; any legacy keys
 * expire naturally at TTL (1 hour).</p>
 *
 * <p>TASK-BE-606: no longer observability-only — {@code TokenReuseRule} alerts on count 1
 * and locks from count 2 within the TTL window; on a Redis error this returns 0 and the
 * rule locks (fail-closed).</p>
 *
 * <p>TASK-BE-608: {@code INCR} + {@code EXPIRE} used to be two separate round-trips
 * (see git history) — if the process died, the connection dropped, or Redis itself failed
 * between the two calls, the key was left with no TTL and became permanent. Every later
 * reuse for that account then read a non-zero, ever-growing count and (since BE-606 made
 * the count drive the score) locked forever. Both commands now run inside a single Lua
 * script ({@link #INCREMENT_SCRIPT}, same shape as {@code TokenBucketRateLimiter}'s), which
 * Redis executes as one atomic unit — there is no window in which the increment is visible
 * without its expiry. The TTL is still set only on the first increment of the window (value
 * == 1), preserving the existing "TTL from first increment" semantics (fixed window, not a
 * sliding one) — counting behaviour is unchanged, only the atomicity of the two writes is
 * new.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenReuseCounter implements TokenReuseCounter {

    private static final String PREFIX = "reuse:";
    private static final Duration TTL = Duration.ofHours(1);

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT;

    static {
        INCREMENT_SCRIPT = new DefaultRedisScript<>();
        INCREMENT_SCRIPT.setScriptText(
                "local current = redis.call('INCR', KEYS[1])\n" +
                "if current == 1 then\n" +
                "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "end\n" +
                "return current"
        );
        INCREMENT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    @Override
    public long incrementAndGet(String tenantId, String accountId) {
        String key = key(tenantId, accountId);
        try {
            Long value = redisTemplate.execute(INCREMENT_SCRIPT,
                    List.of(key), String.valueOf(TTL.toSeconds()));
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("Redis token-reuse INCR failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    @Override
    public long peek(String tenantId, String accountId) {
        try {
            String v = redisTemplate.opsForValue().get(key(tenantId, accountId));
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Redis token-reuse GET failed for tenantId={}, accountId={}; returning 0",
                    tenantId, accountId, e);
            return 0L;
        }
    }

    private static String key(String tenantId, String accountId) {
        return PREFIX + tenantId + ":" + accountId;
    }
}
