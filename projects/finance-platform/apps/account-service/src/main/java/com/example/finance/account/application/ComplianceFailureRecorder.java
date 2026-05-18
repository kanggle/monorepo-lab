package com.example.finance.account.application;

import com.example.common.id.UuidV7;
import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.finance.account.domain.account.ActorType;
import com.example.finance.account.domain.audit.AuditLog;
import com.example.finance.account.domain.audit.AuditLogRepository;
import com.example.finance.account.domain.compliance.ComplianceReviewQueueEntry;
import com.example.finance.account.domain.compliance.ComplianceReviewQueueRepository;
import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Persists a compliance rejection (SANCTION_HIT / AML unresolved) in its OWN
 * transaction so the FAILED transaction + operator-queue row + audit row +
 * outbox events SURVIVE even though the calling fund-movement transaction
 * rolls back (fintech F4: sanction hit → txn FAILED + operator queue, no
 * auto-clear; the fund movement itself must NOT commit).
 *
 * <p>{@code REQUIRES_NEW} is the structural mechanism that keeps F4's "the
 * rejection is recorded, the funds are not moved" invariant: the outer Tx
 * rolls back (no balance change), this inner Tx commits (the FAILED txn +
 * queue entry are durable). The 422 error is then surfaced to the caller.
 */
@Component
@RequiredArgsConstructor
public class ComplianceFailureRecorder {

    private static final String AGG_ACCOUNT = "account";

    private final TransactionRepository transactionRepository;
    private final ComplianceReviewQueueRepository reviewQueueRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountEventPublisher eventPublisher;

    /** SANCTION_HIT: durable FAILED txn + operator-queue row + event. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSanctionHit(Transaction txn,
                                  String tenantId,
                                  String accountId,
                                  String screeningRef,
                                  Instant now) {
        txn.markFailed("SANCTION_HIT");
        Transaction failed = transactionRepository.save(txn);
        ComplianceReviewQueueEntry queued = reviewQueueRepository.save(
                ComplianceReviewQueueEntry.sanctionHit(UuidV7.randomString(),
                        tenantId, accountId, failed.getId(), screeningRef, now));
        auditLogRepository.save(AuditLog.of(tenantId, AGG_ACCOUNT, accountId,
                "SANCTION_HIT", null, ActorType.COMPLIANCE, null,
                "{\"transactionId\":\"" + failed.getId()
                        + "\",\"screeningRef\":\"" + screeningRef + "\"}",
                "sanction/watchlist match", now));
        eventPublisher.publishTransactionFailed(failed);
        eventPublisher.publishSanctionHit(accountId, failed.getId(),
                screeningRef, queued.getId());
    }

    /** AML unresolved: durable FAILED txn + event (no operator queue row). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAmlUnresolved(Transaction txn) {
        txn.markFailed("AML_SCREENING_REQUIRED");
        Transaction failed = transactionRepository.save(txn);
        eventPublisher.publishTransactionFailed(failed);
    }
}
