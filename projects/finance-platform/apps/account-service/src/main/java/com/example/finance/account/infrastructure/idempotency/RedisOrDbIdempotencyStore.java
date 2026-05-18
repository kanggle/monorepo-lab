package com.example.finance.account.infrastructure.idempotency;

import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.domain.error.DomainErrors.IdempotencyStoreUnavailableException;
import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaEntity;
import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Idempotency store (fintech F1, transactional T1; architecture.md §
 * Idempotency). Redis is the primary (atomic read); the
 * {@code idempotency_keys} table is the fallback when Redis is offline.
 *
 * <p><b>Fail-CLOSED</b>: if BOTH Redis and the DB are unreachable on lookup,
 * {@link IdempotencyStoreUnavailableException} (→ 503) is raised — for a
 * mutating fund write the idempotency guarantee outweighs availability (F1),
 * so we refuse rather than risk a double fund movement.
 *
 * <p>Key scope = {@code (idempotencyKey, endpoint, tenantId)}.
 */
@Slf4j
@Component
public class RedisOrDbIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "finance:account:idem:";

    private final StringRedisTemplate redis;
    private final IdempotencyKeyJpaRepository jpa;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisOrDbIdempotencyStore(
            StringRedisTemplate redis,
            IdempotencyKeyJpaRepository jpa,
            ObjectMapper objectMapper,
            @Value("${financeplatform.account.idempotency.ttl-seconds:86400}")
            long ttlSeconds) {
        this.redis = redis;
        this.jpa = jpa;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    private static String redisKey(String tenantId, String endpoint, String key) {
        return PREFIX + tenantId + ":" + endpoint + ":" + key;
    }

    @Override
    public Lookup findExisting(String tenantId, String endpoint,
                               String key, String payloadHash) {
        boolean redisDown = false;
        try {
            String raw = redis.opsForValue().get(redisKey(tenantId, endpoint, key));
            if (raw != null) {
                Wire w = readWire(raw);
                return w.payloadHash().equals(payloadHash)
                        ? Lookup.replay(new StoredResponse(w.status(), w.body()))
                        : Lookup.conflict();
            }
        } catch (DataAccessException e) {
            redisDown = true;
            log.warn("Idempotency Redis lookup failed, falling back to DB: {}",
                    e.getMessage());
        }
        try {
            var row = jpa.findByIdempotencyKeyAndEndpointAndTenantId(
                    key, endpoint, tenantId);
            if (row.isEmpty()) {
                return Lookup.miss();
            }
            var r = row.get();
            return r.getPayloadHash().equals(payloadHash)
                    ? Lookup.replay(new StoredResponse(
                            r.getResponseStatus(), r.getResponseBody()))
                    : Lookup.conflict();
        } catch (DataAccessException dbEx) {
            if (redisDown) {
                // Both stores down → fail-CLOSED (F1).
                throw new IdempotencyStoreUnavailableException(
                        "Idempotency store unavailable (Redis + DB down)");
            }
            log.warn("Idempotency DB lookup failed (Redis was up, miss): {}",
                    dbEx.getMessage());
            return Lookup.miss();
        }
    }

    @Override
    public void store(String tenantId, String endpoint, String key,
                      String payloadHash, StoredResponse response) {
        String serialized = serialize(payloadHash, response);
        boolean redisStored = false;
        try {
            redis.opsForValue().set(redisKey(tenantId, endpoint, key), serialized, ttl);
            redisStored = true;
        } catch (DataAccessException e) {
            log.warn("Idempotency Redis store failed, using DB fallback: {}",
                    e.getMessage());
        }
        try {
            Instant now = Instant.now();
            jpa.save(IdempotencyKeyJpaEntity.of(key, endpoint, tenantId, payloadHash,
                    response.status(), response.body(), now, now.plus(ttl)));
        } catch (DataAccessException dbEx) {
            if (!redisStored) {
                throw new IdempotencyStoreUnavailableException(
                        "Idempotency store unavailable on write (Redis + DB down)");
            }
            log.warn("Idempotency DB store failed (Redis ok): {}", dbEx.getMessage());
        }
    }

    private String serialize(String payloadHash, StoredResponse r) {
        try {
            return objectMapper.writeValueAsString(
                    new Wire(payloadHash, r.status(), r.body()));
        } catch (Exception e) {
            throw new IllegalStateException("idempotency serialize failed", e);
        }
    }

    private Wire readWire(String raw) {
        try {
            return objectMapper.readValue(raw, Wire.class);
        } catch (Exception e) {
            throw new IllegalStateException("idempotency deserialize failed", e);
        }
    }

    private record Wire(String payloadHash, int status, String body) {
    }
}
