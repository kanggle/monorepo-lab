package com.example.finance.account.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyJpaRepository
        extends JpaRepository<IdempotencyKeyJpaEntity, IdempotencyKeyJpaEntity.Pk> {

    Optional<IdempotencyKeyJpaEntity> findByIdempotencyKeyAndEndpointAndTenantId(
            String idempotencyKey, String endpoint, String tenantId);

    /**
     * Atomic claim insert (fintech F1). The composite PK
     * {@code (idempotency_key, endpoint, tenant_id)} is the authoritative
     * concurrency gate: exactly one concurrent caller inserts the sentinel
     * row ({@code response_status = 0}, no body); duplicates raise
     * {@code DataIntegrityViolationException} and lose the claim. NOT
     * {@code save()} — Spring Data {@code save} merges (upsert) and would
     * not fail on a duplicate key.
     */
    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (idempotency_key, endpoint, "
            + "tenant_id, payload_hash, response_status, response_body, "
            + "created_at, expires_at) VALUES (:k, :ep, :t, :ph, 0, NULL, "
            + ":now, :exp)", nativeQuery = true)
    int insertClaim(@Param("k") String key,
                    @Param("ep") String endpoint,
                    @Param("t") String tenantId,
                    @Param("ph") String payloadHash,
                    @Param("now") Instant now,
                    @Param("exp") Instant expiresAt);

    /** Fill a won claim with the final response (sentinel → completed). */
    @Modifying
    @Query("UPDATE IdempotencyKeyJpaEntity e SET e.responseStatus = :st, "
            + "e.responseBody = :body WHERE e.idempotencyKey = :k "
            + "AND e.endpoint = :ep AND e.tenantId = :t")
    int markCompleted(@Param("k") String key,
                      @Param("ep") String endpoint,
                      @Param("t") String tenantId,
                      @Param("st") int status,
                      @Param("body") String body);

    /** Release a claim (action failed, or reclaim a stale/expired sentinel). */
    @Modifying
    int deleteByIdempotencyKeyAndEndpointAndTenantId(
            String idempotencyKey, String endpoint, String tenantId);
}
