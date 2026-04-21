package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.infrastructure.util.TokenKeyHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis Sorted Set 기반 사용자 세션 레지스트리.
 *
 * Key:    {namespace}:sessions:{userId}
 * Member: SHA-256(refreshToken)
 * Score:  마지막 활동 시간 (epoch millis)
 *
 * 주의: Lua 스크립트 내에서 refreshPrefix를 동적으로 조합해 DEL을 호출한다.
 * 단일 Redis 인스턴스 환경에서만 안전하다 (Redis Cluster는 KEYS 선언 필요).
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class RedisUserSessionRegistry implements UserSessionRegistry {

    /**
     * 세션 등록 Lua 스크립트.
     *
     * KEYS[1] = {namespace}:sessions:{userId}
     * ARGV[1] = cutoffMillis  — 이 값보다 오래된(score가 작은) 세션을 비활성으로 판단
     * ARGV[2] = maxSessions
     * ARGV[3] = nowMillis     — 새 세션의 score
     * ARGV[4] = newSessionHash — SHA-256(refreshToken)
     * ARGV[5] = inactivityTimeoutSeconds — sorted set 키 TTL
     * ARGV[6] = refreshKeyPrefix — 제거된 세션의 refresh token 삭제에 사용
     *
     * Returns: 제거된 세션의 hash, 없으면 false(null)
     */
    private static final DefaultRedisScript<String> REGISTER_SCRIPT;

    /**
     * 세션 교체 Lua 스크립트 (토큰 갱신 시).
     *
     * KEYS[1] = {namespace}:sessions:{userId}
     * ARGV[1] = oldSessionHash
     * ARGV[2] = nowMillis
     * ARGV[3] = newSessionHash
     * ARGV[4] = inactivityTimeoutSeconds
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT;

    static {
        REGISTER_SCRIPT = new DefaultRedisScript<>();
        REGISTER_SCRIPT.setScriptText(
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '0', ARGV[1])\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "local evictedHash = false\n" +
            "if count >= tonumber(ARGV[2]) then\n" +
            "    local oldest = redis.call('ZRANGE', KEYS[1], '0', '0')\n" +
            "    if #oldest > 0 then\n" +
            "        evictedHash = oldest[1]\n" +
            "        redis.call('ZREM', KEYS[1], evictedHash)\n" +
            "        redis.call('DEL', ARGV[6] .. evictedHash)\n" +
            "    end\n" +
            "end\n" +
            "redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[5])\n" +
            "return evictedHash"
        );
        REGISTER_SCRIPT.setResultType(String.class);

        ROTATE_SCRIPT = new DefaultRedisScript<>();
        ROTATE_SCRIPT.setScriptText(
            "redis.call('ZREM', KEYS[1], ARGV[1])\n" +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[4])\n" +
            "return 1"
        );
        ROTATE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final SessionProperties sessionProperties;
    private final String sessionPrefix;
    private final String refreshPrefix;

    public RedisUserSessionRegistry(StringRedisTemplate redisTemplate,
                                    SessionProperties sessionProperties,
                                    @Value("${app.redis.key-namespace:auth}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.sessionProperties = sessionProperties;
        this.sessionPrefix = namespace + ":sessions:";
        this.refreshPrefix = namespace + ":refresh:";
    }

    @Override
    public UserSessionRegistry.RegistrationResult registerSession(UUID userId, String refreshToken,
                                                                  long inactivityTimeoutSeconds) {
        String newHash = TokenKeyHasher.sha256Hex(refreshToken);
        long nowMillis = Instant.now().toEpochMilli();
        long cutoffMillis = nowMillis - inactivityTimeoutSeconds * 1000L;

        String evictedHash = redisTemplate.execute(
            REGISTER_SCRIPT,
            List.of(sessionKey(userId)),
            String.valueOf(cutoffMillis),
            String.valueOf(sessionProperties.maxConcurrentSessions()),
            String.valueOf(nowMillis),
            newHash,
            String.valueOf(inactivityTimeoutSeconds),
            refreshPrefix
        );
        return new UserSessionRegistry.RegistrationResult(newHash, evictedHash);
    }

    @Override
    public void rotateSession(UUID userId, String oldRefreshToken, String newRefreshToken,
                              long inactivityTimeoutSeconds) {
        String oldHash = TokenKeyHasher.sha256Hex(oldRefreshToken);
        String newHash = TokenKeyHasher.sha256Hex(newRefreshToken);
        long nowMillis = Instant.now().toEpochMilli();

        redisTemplate.execute(
            ROTATE_SCRIPT,
            List.of(sessionKey(userId)),
            oldHash,
            String.valueOf(nowMillis),
            newHash,
            String.valueOf(inactivityTimeoutSeconds)
        );
    }

    @Override
    public void removeSession(UUID userId, String refreshToken) {
        String hash = TokenKeyHasher.sha256Hex(refreshToken);
        redisTemplate.opsForZSet().remove(sessionKey(userId), hash);
    }

    @Override
    public void removeAllSessions(UUID userId) {
        redisTemplate.delete(sessionKey(userId));
    }

    private String sessionKey(UUID userId) {
        return sessionPrefix + userId;
    }
}
