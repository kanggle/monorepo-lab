package com.example.finance.account.infrastructure.idempotency;

import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaEntity;
import com.example.finance.account.infrastructure.persistence.jpa.IdempotencyKeyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * One-DB-operation-per-transaction boundary for the idempotency claim
 * (fintech F1). <b>Each method is its own {@code REQUIRES_NEW} transaction
 * containing exactly ONE statement.</b>
 *
 * <p>This is a <em>separate bean</em> (not self-invoked methods on
 * {@link RedisOrDbIdempotencyStore}) for two reasons:
 * <ol>
 *   <li>Spring AOP {@code @Transactional} does not apply to self-invocation;</li>
 *   <li>once a constraint violation occurs inside a transaction that
 *       transaction is marked rollback-only — catching the
 *       {@code DataIntegrityViolationException} <em>inside</em> the same
 *       transaction and continuing with more JPA work fails the commit with
 *       {@code UnexpectedRollbackException}. The duplicate-key path must
 *       therefore throw OUT of this single-statement transaction so the
 *       non-transactional orchestrator catches it cleanly and then opens a
 *       fresh transaction for the follow-up read.</li>
 * </ol>
 *
 * <p>{@code REQUIRES_NEW} also guarantees the winning claim row is committed
 * and immediately visible to the concurrent same-key burst (the prior
 * non-atomic store wrote AFTER the action and could not gate it). Mirrors the
 * {@code ComplianceFailureRecorder} REQUIRES_NEW precedent.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyTx {

    private final IdempotencyKeyJpaRepository jpa;

    /**
     * Atomic claim insert. The composite PK is the concurrency gate: exactly
     * one concurrent caller succeeds; the rest raise
     * {@code DataIntegrityViolationException}, which rolls back ONLY this
     * single-statement transaction and propagates to the (non-transactional)
     * caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertClaim(String key, String endpoint, String tenantId,
                            String payloadHash, Instant now, Instant expiresAt) {
        jpa.insertClaim(key, endpoint, tenantId, payloadHash, now, expiresAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKeyJpaEntity> find(String key, String endpoint,
                                                  String tenantId) {
        return jpa.findByIdempotencyKeyAndEndpointAndTenantId(
                key, endpoint, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String key, String endpoint, String tenantId,
                              int status, String body) {
        jpa.markCompleted(key, endpoint, tenantId, status, body);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(String key, String endpoint, String tenantId) {
        jpa.deleteByIdempotencyKeyAndEndpointAndTenantId(key, endpoint, tenantId);
    }
}
