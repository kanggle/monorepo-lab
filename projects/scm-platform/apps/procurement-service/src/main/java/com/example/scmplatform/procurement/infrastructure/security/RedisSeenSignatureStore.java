package com.example.scmplatform.procurement.infrastructure.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link SeenSignatureStore}. Uses {@code SET key value NX EX ttl}
 * (via {@code setIfAbsent}) so the freshness check and the record write are a
 * single atomic operation — two concurrent replays cannot both observe the
 * nonce as absent.
 */
@Component
public class RedisSeenSignatureStore implements SeenSignatureStore {

    private static final String KEY_PREFIX = "scm:procurement:webhook:nonce:";

    private final StringRedisTemplate redis;

    public RedisSeenSignatureStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean markIfFresh(String signatureHex, Duration ttl) {
        Boolean newlyRecorded = redis.opsForValue()
                .setIfAbsent(KEY_PREFIX + signatureHex, "1", ttl);
        return Boolean.TRUE.equals(newlyRecorded);
    }
}
