package com.example.auth.infrastructure.redis;

import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.repository.OAuthStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed {@link OAuthStateStore}. Uses the {@code oauth:state:{state}}
 * key namespace with a 10-minute TTL (per
 * {@code specs/features/oauth-social-login.md}, TASK-BE-087).
 *
 * <p>{@link #consumeAtomic} performs a single {@code GETDEL} so the read and
 * delete cannot be split between two callers — preventing a replay window
 * between expiry check and deletion.
 *
 * <p>Fail-closed: a {@link org.springframework.dao.DataAccessException} from
 * Redis is allowed to propagate unchanged so that a backing-store outage
 * cannot be silently absorbed and treated as a successful state match.
 */
@Component
@RequiredArgsConstructor
public class RedisOAuthStateStore implements OAuthStateStore {

    static final String KEY_PREFIX = "oauth:state:";
    // Aligned with specs/features/oauth-social-login.md (TASK-BE-087).
    static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void store(String state, OAuthProvider provider) {
        redisTemplate.opsForValue().set(KEY_PREFIX + state, provider.name(), STATE_TTL);
    }

    @Override
    public Optional<OAuthProvider> consumeAtomic(String state) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(OAuthProvider.valueOf(value));
        } catch (IllegalArgumentException e) {
            // Stored value is malformed (provider rename / data corruption).
            // Treat as unknown so the caller surfaces InvalidOAuthStateException
            // rather than blindly trusting a non-canonical provider string.
            return Optional.empty();
        }
    }
}
