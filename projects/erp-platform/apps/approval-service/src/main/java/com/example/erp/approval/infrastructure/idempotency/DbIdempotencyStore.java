package com.example.erp.approval.infrastructure.idempotency;

import com.example.erp.approval.application.port.outbound.IdempotencyStore;
import com.example.erp.approval.domain.error.ApprovalErrors.IdempotencyStoreUnavailableException;
import com.example.erp.approval.infrastructure.persistence.jpa.IdempotencyKeyJpaEntity;
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
 * <b>Atomic claim-before-execute</b>: the {@code idempotency_keys} composite PK
 * is the concurrency gate — exactly one concurrent caller wins the claim;
 * losers see CONFLICT / REPLAY / IN_PROGRESS.
 *
 * <p>v1 = DB-only (operator-scale traffic; the {@code spring-data-redis} dep is
 * present for future use only). <b>Not {@code @Transactional}</b> — each DB op
 * is a single-statement {@code REQUIRES_NEW} transaction on
 * {@link IdempotencyKeyTx}; the duplicate-key exception is caught HERE, outside
 * that transaction. <b>Fail-CLOSED</b>: DB unreachable on a claim →
 * {@link IdempotencyStoreUnavailableException} (→ 503).
 */
@Slf4j
@Component
public class DbIdempotencyStore implements IdempotencyStore {

    private final IdempotencyKeyTx tx;
    private final Duration ttl;

    public DbIdempotencyStore(
            IdempotencyKeyTx tx,
            @Value("${erpplatform.approval.idempotency.ttl-seconds:86400}")
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
