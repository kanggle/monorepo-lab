package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.RefreshTokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.example.auth.infrastructure.util.TokenKeyHasher;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final DefaultRedisScript<Long> INVALIDATE_SCRIPT;

    static {
        INVALIDATE_SCRIPT = new DefaultRedisScript<>();
        INVALIDATE_SCRIPT.setScriptText(
            "local deleted = redis.call('DEL', KEYS[1])\n" +
            "redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])\n" +
            "return deleted"
        );
        INVALIDATE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final String refreshPrefix;
    private final String revokedPrefix;
    private final String userTokensPrefix;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate,
                                  @Value("${app.redis.key-namespace:auth}") String namespace) {
        this.redisTemplate = redisTemplate;
        this.refreshPrefix = namespace + ":refresh:";
        this.revokedPrefix = namespace + ":revoked:";
        this.userTokensPrefix = namespace + ":user-tokens:";
    }

    @Override
    public void save(String token, UUID userId, long ttlSeconds) {
        String tokenHash = TokenKeyHasher.sha256Hex(token);
        redisTemplate.opsForValue().set(
            refreshPrefix + tokenHash,
            userId.toString(),
            Duration.ofSeconds(ttlSeconds)
        );
        String userTokensKey = userTokensPrefix + userId;
        redisTemplate.opsForSet().add(userTokensKey, tokenHash);
        redisTemplate.expire(userTokensKey, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<UUID> findUserIdByToken(String token) {
        String value = redisTemplate.opsForValue().get(key(token));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            log.warn("Corrupted UUID in refresh token store: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(token)));
    }

    @Override
    public boolean invalidate(String token, long revokedTtlSeconds) {
        String tokenHash = TokenKeyHasher.sha256Hex(token);

        // 보조 인덱스 정리를 위해 userId 조회 (DEL 전에)
        String userIdStr = redisTemplate.opsForValue().get(refreshPrefix + tokenHash);

        Long deleted = redisTemplate.execute(
            INVALIDATE_SCRIPT,
            List.of(refreshPrefix + tokenHash, revokedPrefix + tokenHash),
            String.valueOf(revokedTtlSeconds)
        );

        // 보조 인덱스에서 제거 — fail-open
        if (userIdStr != null) {
            try {
                redisTemplate.opsForSet().remove(userTokensPrefix + userIdStr, tokenHash);
            } catch (Exception e) {
                log.warn("Failed to remove tokenHash from user index: userId={}", userIdStr, e);
            }
        }

        return deleted != null && deleted > 0;
    }

    @Override
    public Set<String> findAllTokenHashesByUserId(UUID userId) {
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userTokensPrefix + userId);
        if (tokenHashes == null) {
            return Collections.emptySet();
        }
        return tokenHashes;
    }

    @Override
    public void invalidateAllByUserId(UUID userId, long revokedTtlSeconds) {
        Set<String> tokenHashes = findAllTokenHashesByUserId(userId);
        for (String tokenHash : tokenHashes) {
            try {
                redisTemplate.execute(
                    INVALIDATE_SCRIPT,
                    List.of(refreshPrefix + tokenHash, revokedPrefix + tokenHash),
                    String.valueOf(revokedTtlSeconds)
                );
            } catch (Exception e) {
                log.error("Failed to invalidate refresh token hash={} for userId={}", tokenHash, userId, e);
            }
        }
        redisTemplate.delete(userTokensPrefix + userId);
        log.info("All refresh tokens invalidated for userId={}, count={}", userId, tokenHashes.size());
    }

    private String key(String token) {
        return refreshPrefix + TokenKeyHasher.sha256Hex(token);
    }

    private String revokedKey(String token) {
        return revokedPrefix + TokenKeyHasher.sha256Hex(token);
    }
}
