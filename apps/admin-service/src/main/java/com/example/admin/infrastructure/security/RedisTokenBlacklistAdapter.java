package com.example.admin.infrastructure.security;

import com.example.admin.application.port.TokenBlacklistPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed adapter for {@link TokenBlacklistPort}. Key layout:
 * {@code admin:jti:blacklist:{jti}} with the access-token's remaining TTL.
 *
 * <p>Fail-closed reads (audit-heavy A10): any Redis exception during
 * {@link #isBlacklisted(String)} is treated as {@code true} so the
 * authentication filter rejects the request rather than admitting a token
 * whose revocation status is unknown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    public static final String KEY_PREFIX = "admin:jti:blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String jti, Duration ttl) {
        Duration effective = (ttl == null || ttl.isNegative() || ttl.isZero())
                ? Duration.ofSeconds(1)
                : ttl;
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", effective);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(exists);
        } catch (RuntimeException ex) {
            // Fail-closed: treat lookup failure as "revoked" rather than
            // admitting a token of unknown status.
            log.error("Token blacklist lookup failed for jti={}; failing closed", jti, ex);
            return true;
        }
    }
}
