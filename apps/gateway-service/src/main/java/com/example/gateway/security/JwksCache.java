package com.example.gateway.security;

import com.example.gateway.config.EdgeGatewayProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JWKS cache backed by Redis with in-memory grace period fallback.
 * Fetches from auth-service every 10 minutes, caches in Redis (TTL 600s),
 * and keeps an in-memory copy for grace period when Redis is unavailable.
 */
@Slf4j
@Component
public class JwksCache {

    private static final String REDIS_KEY = "jwks:cache";

    private final JwksClient jwksClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final EdgeGatewayProperties properties;

    /** In-memory fallback for Redis failures */
    private final ConcurrentHashMap<String, PublicKey> inMemoryKeys = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSuccessfulFetch = new AtomicReference<>(Instant.EPOCH);
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public JwksCache(JwksClient jwksClient,
                     ReactiveStringRedisTemplate redisTemplate,
                     ObjectMapper objectMapper,
                     EdgeGatewayProperties properties) {
        this.jwksClient = jwksClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        refreshJwks().subscribe(
                keys -> log.info("Initial JWKS load completed with {} keys", keys.size()),
                e -> log.error("Initial JWKS load failed: {}", e.getMessage())
        );
    }

    /**
     * Gets the public key for the given kid.
     * Tries Redis first, falls back to in-memory cache within grace period.
     */
    public Mono<Optional<PublicKey>> getPublicKey(String kid) {
        return getKeysFromRedis()
                .map(keys -> Optional.ofNullable(keys.get(kid)))
                .onErrorResume(e -> {
                    log.warn("Redis JWKS cache read failed, using in-memory fallback: {}", e.getMessage());
                    return Mono.just(getFromInMemory(kid));
                });
    }

    /**
     * Triggers an immediate JWKS refresh (e.g., on kid mismatch).
     * Uses single-flight pattern to prevent concurrent refreshes.
     */
    public Mono<Map<String, PublicKey>> refreshJwks() {
        if (!refreshing.compareAndSet(false, true)) {
            log.debug("JWKS refresh already in progress, skipping");
            return Mono.just(new LinkedHashMap<>(inMemoryKeys));
        }

        return jwksClient.fetchJwks()
                .flatMap(keys -> storeInRedis(keys).thenReturn(keys))
                .doOnSuccess(keys -> {
                    inMemoryKeys.clear();
                    inMemoryKeys.putAll(keys);
                    lastSuccessfulFetch.set(Instant.now());
                    log.info("JWKS refreshed: {} keys cached", keys.size());
                })
                .doOnError(e -> log.error("JWKS refresh failed: {}", e.getMessage()))
                .doFinally(signal -> refreshing.set(false));
    }

    @Scheduled(fixedDelayString = "${gateway.jwt.jwks-refresh-interval-ms:600000}")
    public void scheduledRefresh() {
        refreshJwks().subscribe(
                keys -> log.debug("Scheduled JWKS refresh completed"),
                e -> log.error("Scheduled JWKS refresh failed: {}", e.getMessage())
        );
    }

    private Mono<Map<String, PublicKey>> getKeysFromRedis() {
        return redisTemplate.opsForValue().get(REDIS_KEY)
                .flatMap(this::parseKeysFromJson)
                .switchIfEmpty(refreshJwks());
    }

    private Mono<Void> storeInRedis(Map<String, PublicKey> keys) {
        try {
            Map<String, Map<String, String>> serialized = new LinkedHashMap<>();
            for (Map.Entry<String, PublicKey> entry : keys.entrySet()) {
                java.security.interfaces.RSAPublicKey rsaKey =
                        (java.security.interfaces.RSAPublicKey) entry.getValue();
                Map<String, String> keyData = new LinkedHashMap<>();
                keyData.put("n", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rsaKey.getModulus().toByteArray()));
                keyData.put("e", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(rsaKey.getPublicExponent().toByteArray()));
                serialized.put(entry.getKey(), keyData);
            }
            String json = objectMapper.writeValueAsString(serialized);
            Duration ttl = Duration.ofSeconds(properties.getJwt().getJwksCacheTtlSeconds());
            return redisTemplate.opsForValue().set(REDIS_KEY, json, ttl).then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, PublicKey>> parseKeysFromJson(String json) {
        return Mono.fromCallable(() -> {
            Map<String, Map<String, String>> serialized = objectMapper.readValue(json,
                    new TypeReference<Map<String, Map<String, String>>>() {});
            Map<String, PublicKey> keys = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : serialized.entrySet()) {
                Map<String, String> keyData = entry.getValue();
                BigInteger modulus = new BigInteger(1,
                        Base64.getUrlDecoder().decode(keyData.get("n")));
                BigInteger exponent = new BigInteger(1,
                        Base64.getUrlDecoder().decode(keyData.get("e")));
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
                keys.put(entry.getKey(), publicKey);
            }
            return keys;
        });
    }

    private Optional<PublicKey> getFromInMemory(String kid) {
        Instant lastFetch = lastSuccessfulFetch.get();
        long graceSeconds = properties.getJwt().getGracePeriodSeconds();
        if (Instant.now().isBefore(lastFetch.plusSeconds(graceSeconds))) {
            return Optional.ofNullable(inMemoryKeys.get(kid));
        }
        log.warn("In-memory JWKS grace period expired. Last fetch: {}", lastFetch);
        return Optional.empty();
    }
}
