package com.example.erp.masterdata.infrastructure.idempotency;

import com.example.erp.masterdata.application.port.outbound.IdempotencyStore;
import com.example.erp.masterdata.domain.error.DomainErrors.IdempotencyStoreUnavailableException;
import com.example.erp.masterdata.infrastructure.persistence.jpa.IdempotencyKeyJpaEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * DB-PK-authoritative idempotency store (architecture.md § Idempotency).
 * Implements <b>atomic claim-before-execute</b>: the {@code idempotency_keys}
 * composite PK is the concurrency gate — exactly one concurrent caller wins
 * the claim; losers see CONFLICT / REPLAY / IN_PROGRESS.
 *
 * <p>v1 = DB-only (architecture.md § Idempotency — operator-scale traffic,
 * Redis primary not wired in v1; the {@code spring-data-redis} dep is present
 * for future use only). The DB-table primary inside the mutation Tx is
 * sufficient; this design is the FIN-BE-004 final form minus the Redis
 * fast-path.
 *
 * <p><b>This orchestrator is intentionally NOT {@code @Transactional}.</b>
 * Each DB operation is a single-statement {@code REQUIRES_NEW} transaction
 * on {@link IdempotencyKeyTx}. The duplicate-key
 * {@link DataIntegrityViolationException} is caught HERE — outside that
 * transaction — so a poisoned (rollback-only) transaction is never continued.
 *
 * <p><b>Fail-CLOSED</b>: if the authoritative DB is unreachable on a claim,
 * {@link IdempotencyStoreUnavailableException} (→ 503) is raised.
 */
@Slf4j
@Component
public class DbIdempotencyStore implements IdempotencyStore {

    private final IdempotencyKeyTx tx;
    private final Duration ttl;

    public DbIdempotencyStore(
            IdempotencyKeyTx tx,
            @Value("${erpplatform.masterdata.idempotency.ttl-seconds:86400}")
            long ttlSeconds) {
        this.tx = tx;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Claim claim(String tenantId, String endpoint, String key, String payloadHash) {
        Instant now = Instant.now();
        try {
            tx.insertClaim(key, endpoint, tenantId, payloadHash, now, now.plus(ttl));
            return Claim.execute();
        } catch (DataIntegrityViolationException dup) {
            return resolveExisting(tenantId, endpoint, key, payloadHash, now);
        } catch (DataAccessException dbDown) {
            throw new IdempotencyStoreUnavailableException(
                    "Idempotency store unavailable on claim (DB down)");
        }
    }

    private Claim resolveExisting(String tenantId, String endpoint, String key,
                                  String payloadHash, Instant now) {
        Optional<IdempotencyKeyJpaEntity> rowOpt = tx.find(key, endpoint, tenantId);
        if (rowOpt.isEmpty()) {
            try {
                tx.insertClaim(key, endpoint, tenantId, payloadHash, now, now.plus(ttl));
                return Claim.execute();
            } catch (DataIntegrityViolationException stillThere) {
                rowOpt = tx.find(key, endpoint, tenantId);
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
            return Claim.replay(new StoredResponse(r.getResponseStatus(), r.getResponseBody()));
        }
        if (r.getExpiresAt().isBefore(now)) {
            tx.delete(key, endpoint, tenantId);
            try {
                tx.insertClaim(key, endpoint, tenantId, payloadHash, now, now.plus(ttl));
                return Claim.execute();
            } catch (DataIntegrityViolationException reclaimedByOther) {
                return Claim.inProgress();
            }
        }
        return Claim.inProgress();
    }

    @Override
    public void complete(String tenantId, String endpoint, String key,
                         String payloadHash, StoredResponse response) {
        tx.markCompleted(key, endpoint, tenantId, response.status(), response.body());
    }

    @Override
    public void release(String tenantId, String endpoint, String key) {
        tx.delete(key, endpoint, tenantId);
    }
}
