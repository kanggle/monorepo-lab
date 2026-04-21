package com.example.auth.infrastructure.redis;

import com.example.auth.domain.service.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis fixed-window rate limiter.
 * Allows at most MAX_REQUESTS per WINDOW_SECONDS per clientKey.
 * Uses an atomic Lua script (INCR + EXPIRE) to prevent race conditions.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisRateLimiter(StringRedisTemplate redisTemplate,
                            @Value("${app.redis.key-namespace:auth}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = namespace + ":ratelimit:";
    }

    @Override
    public boolean isRateLimited(String clientKey, int maxRequests, long windowSeconds) {
        String key = keyPrefix + clientKey;
        try {
            Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(windowSeconds)
            );
            if (count == null) {
                // Lua 스크립트 결과가 null인 경우는 비정상 상황. fail-open으로 통과시킨다.
                log.error("Rate limit script returned null for clientKey={}, failing open", clientKey);
                return false;
            }
            return count > maxRequests;
        } catch (DataAccessException e) {
            // Redis 장애 시 fail-open: 요청을 차단하지 않고 통과시킨다.
            // rate limiter 실패가 서비스 전체 중단보다 낫다.
            log.error("Rate limit check failed due to Redis error: clientKey={}", clientKey, e);
            return false;
        }
    }
}
