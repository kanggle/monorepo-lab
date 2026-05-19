package com.example.finance.account.infrastructure.idempotency;

import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.domain.error.DomainErrors.IdempotencyStoreUnavailableException;
import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaEntity;
import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency store (fintech F1, transactional T1; architecture.md §
 * Idempotency). Implements <b>atomic claim-before-execute</b>: the
 * {@code idempotency_keys} composite PK {@code (idempotency_key, endpoint,
 * tenant_id)} is the authoritative concurrency gate — exactly one concurrent
 * caller inserts the sentinel claim row ({@code response_status = 0}), the
 * rest see it and REPLAY / IN_PROGRESS / CONFLICT. Redis is a best-effort
 * accelerator for COMPLETED replays only (never the correctness authority),
 * realising the architecture.md "Redis primary" read fast-path while the
 * stronger DB gate subsumes the previous non-atomic check-then-act.
 *
 * <p><b>Fail-CLOSED</b>: if the authoritative DB is unreachable on a claim for
 * a mutating fund write, {@link IdempotencyStoreUnavailableException} (→ 503)
 * is raised — the idempotency guarantee outweighs availability (F1).
 *
 * <p>Each method runs in its own {@code REQUIRES_NEW} transaction so the claim
 * row is committed and immediately visible to concurrent requests (the
 * non-atomic predecessor stored AFTER the action — it could not gate a
 * concurrent same-key burst). Mirrors the {@code ComplianceFailureRecorder}
 * REQUIRES_NEW precedent. {@code IdempotentExecution.run} is a non-transactional
 * presentation helper; the fund-moving action owns its own boundary.
 *
 * <p>Key scope = {@code (idempotencyKey, endpoint, tenantId)}; TTL ≥ 24h; an
 * expired in-progress sentinel (crashed executor) is reclaimable.
 */
@Slf4j
@Component
public class RedisOrDbIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "finance:account:idem:";
    /** response_status sentinel for a claimed-but-not-yet-completed row. */
    private static final int IN_PROGRESS_STATUS = 0;

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(String tenantId, String endpoint, String key,
                       String payloadHash) {
        // 1. Redis fast-path: a cached COMPLETED response replays without
        //    touching the DB. Any other Redis state defers to the DB gate.
        try {
            String raw = redis.opsForValue().get(redisKey(tenantId, endpoint, key));
            if (raw != null) {
                Wire w = readWire(raw);
                if (w.status() >= 200 && w.payloadHash().equals(payloadHash)) {
                    return Claim.replay(new StoredResponse(w.status(), w.body()));
                }
            }
        } catch (DataAccessException redisDown) {
            log.warn("Idempotency Redis read failed, DB authoritative: {}",
                    redisDown.getMessage());
        }

        // 2. DB is the authoritative atomic gate.
        Instant now = Instant.now();
        try {
            jpa.insertClaim(key, endpoint, tenantId, payloadHash,
                    now, now.plus(ttl));
            return Claim.execute();                 // we won the claim
        } catch (DataIntegrityViolationException dup) {
            return resolveExisting(tenantId, endpoint, key, payloadHash, now);
        } catch (DataAccessException dbDown) {
            // Authoritative store unreachable for a fund write → fail-CLOSED.
            throw new IdempotencyStoreUnavailableException(
                    "Idempotency store unavailable on claim (DB down)");
        }
    }

    private Claim resolveExisting(String tenantId, String endpoint, String key,
                                  String payloadHash, Instant now) {
        Optional<IdempotencyKeyJpaEntity> rowOpt =
                jpa.findByIdempotencyKeyAndEndpointAndTenantId(key, endpoint, tenantId);
        if (rowOpt.isEmpty()) {
            // Row vanished between the failed insert and this read
            // (concurrent release/reclaim). One bounded retry.
            try {
                jpa.insertClaim(key, endpoint, tenantId, payloadHash,
                        now, now.plus(ttl));
                return Claim.execute();
            } catch (DataIntegrityViolationException stillThere) {
                rowOpt = jpa.findByIdempotencyKeyAndEndpointAndTenantId(
                        key, endpoint, tenantId);
                if (rowOpt.isEmpty()) {
                    throw new IdempotencyStoreUnavailableException(
                            "Idempotency claim race unresolved");
                }
            }
        }
        IdempotencyKeyJpaEntity r = rowOpt.get();
        if (!r.getPayloadHash().equals(payloadHash)) {
            return Claim.conflict();
        }
        if (r.getResponseStatus() >= 200) {
            StoredResponse stored =
                    new StoredResponse(r.getResponseStatus(), r.getResponseBody());
            cache(tenantId, endpoint, key, payloadHash, stored); // refresh fast-path
            return Claim.replay(stored);
        }
        // Sentinel (IN_PROGRESS). Reclaim if the prior executor's claim expired.
        if (r.getExpiresAt().isBefore(now)) {
            jpa.deleteByIdempotencyKeyAndEndpointAndTenantId(key, endpoint, tenantId);
            try {
                jpa.insertClaim(key, endpoint, tenantId, payloadHash,
                        now, now.plus(ttl));
                return Claim.execute();
            } catch (DataIntegrityViolationException reclaimedByOther) {
                return Claim.inProgress();
            }
        }
        return Claim.inProgress();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String tenantId, String endpoint, String key,
                         String payloadHash, StoredResponse response) {
        jpa.markCompleted(key, endpoint, tenantId,
                response.status(), response.body());
        cache(tenantId, endpoint, key, payloadHash, response);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String tenantId, String endpoint, String key) {
        jpa.deleteByIdempotencyKeyAndEndpointAndTenantId(key, endpoint, tenantId);
        try {
            redis.delete(redisKey(tenantId, endpoint, key));
        } catch (DataAccessException ignored) {
            log.warn("Idempotency Redis delete failed (best-effort): {}",
                    ignored.getMessage());
        }
    }

    private void cache(String tenantId, String endpoint, String key,
                       String payloadHash, StoredResponse r) {
        try {
            redis.opsForValue().set(redisKey(tenantId, endpoint, key),
                    serialize(payloadHash, r), ttl);
        } catch (DataAccessException ignored) {
            log.warn("Idempotency Redis cache write failed (best-effort): {}",
                    ignored.getMessage());
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
