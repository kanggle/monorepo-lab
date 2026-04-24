package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.infrastructure.util.TokenKeyHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class RedisAccessTokenBlocklist implements AccessTokenBlocklist {

    private final StringRedisTemplate redisTemplate;
    private final String blockedPrefix;
    private final String blockedUserPrefix;

    public RedisAccessTokenBlocklist(StringRedisTemplate redisTemplate,
                                     @Value("${app.redis.key-namespace:auth}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.blockedPrefix = namespace + ":blocked-at:";
        this.blockedUserPrefix = namespace + ":blocked-user:";
    }

    @Override
    public void block(String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(key(token), "1", Duration.ofSeconds(ttlSeconds));
        log.debug("Access token blocked: ttlSeconds={}", ttlSeconds);
    }

    @Override
    public boolean isBlocked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    @Override
    public void blockByUserId(UUID userId, long ttlSeconds) {
        redisTemplate.opsForValue().set(
            blockedUserPrefix + userId, "1", Duration.ofSeconds(ttlSeconds));
        log.debug("User blocked by userId={}, ttlSeconds={}", userId, ttlSeconds);
    }

    @Override
    public boolean isUserBlocked(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockedUserPrefix + userId));
    }

    private String key(String token) {
        return blockedPrefix + TokenKeyHasher.sha256Hex(token);
    }
}
