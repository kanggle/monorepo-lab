package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationPeriodLockedException;
import com.example.finance.ledger.domain.period.PeriodStatus;
import com.example.finance.ledger.domain.period.repository.AccountingPeriodRepository;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Operator resolution of a reconciliation discrepancy (architecture.md
 * § Reconciliation, fintech F8). One {@code @Transactional} boundary: load the
 * discrepancy (else {@code RECONCILIATION_DISCREPANCY_NOT_FOUND}); call
 * {@link ReconciliationDiscrepancy#resolve} (throws
 * {@code RECONCILIATION_ALREADY_RESOLVED} if not OPEN); save + append the audit
 * row.
 *
 * <p><b>F8 — this is the ONLY path from OPEN to RESOLVED.</b> It is operator-
 * initiated (via the REST endpoint); there is no automatic invocation anywhere.
 * It never posts a balancing journal entry or adjusts the recorded amounts.
 */
@Service
@RequiredArgsConstructor
public class ResolveDiscrepancyUseCase {

    private static final String AGGREGATE_TYPE = "ReconciliationDiscrepancy";

    private final ReconciliationRepository reconciliationRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ClockPort clock;

    @Transactional
    public DiscrepancyView resolve(String discrepancyId, String tenantId,
                                   ResolutionType resolutionType, String note, String actor) {
        ReconciliationDiscrepancy discrepancy =
                reconciliationRepository.findDiscrepancyById(discrepancyId, tenantId)
                        .orElseThrow(() -> new ReconciliationDiscrepancyNotFoundException(
                                "reconciliation discrepancy not found: " + discrepancyId));

        // (6th incr) Period lock — before any mutation/save (a locked discrepancy
        // stays OPEN, no audit row). Net-zero when no CLOSED period covers it.
        guardPeriodLock(discrepancy, tenantId);

        Instant now = clock.now();
        // resolve() throws ReconciliationAlreadyResolvedException if not OPEN.
        discrepancy.resolve(resolutionType, note, actor, now);

        ReconciliationDiscrepancy saved = reconciliationRepository.saveDiscrepancy(discrepancy);
        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, discrepancyId, "RESOLVED",
                actor, auditSummary(saved), "resolve reconciliation discrepancy", now));

        return DiscrepancyView.from(saved);
    }

    /**
     * Period lock (architecture.md § Reconciliation § Period lock, F8 extended to
     * the period boundary). If the discrepancy's owning statement's
     * {@code statementDate} (a {@code LocalDate}, mapped to its start-of-day UTC
     * instant) is covered by a CLOSED accounting period, the closed month's
     * reconciliation is frozen → {@link ReconciliationPeriodLockedException}.
     *
     * <p><b>Net-zero</b>: no {@code statementId} OR the statement is absent OR
     * {@code findCovering(..., CLOSED)} empty (an OPEN period does NOT lock — only
     * CLOSED) → the guard does not fire and {@code resolve} proceeds byte-identically
     * to FIN-BE-010. The half-open {@code [from, to)} parity matches the posting guard.
     */
    private void guardPeriodLock(ReconciliationDiscrepancy discrepancy, String tenantId) {
        String statementId = discrepancy.statementId();
        if (statementId == null) {
            return; // net-zero: no resolvable period
        }
        Optional<ExternalStatement> statement =
                reconciliationRepository.findStatementById(statementId, tenantId);
        if (statement.isEmpty()) {
            return; // net-zero: statement absent
        }
        Instant at = statement.get().statementDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant(); // LocalDate → start-of-day UTC instant
        if (accountingPeriodRepository.findCovering(tenantId, at, PeriodStatus.CLOSED).isPresent()) {
            throw new ReconciliationPeriodLockedException(
                    "reconciliation discrepancy is in a CLOSED accounting period (statementDate="
                            + statement.get().statementDate()
                            + ", discrepancyId=" + discrepancy.discrepancyId() + ")");
        }
    }

    private static String auditSummary(ReconciliationDiscrepancy d) {
        return "discrepancyId=" + d.discrepancyId() + " type=" + d.type()
                + " status=" + d.status() + " resolutionType=" + d.resolutionType();
    }
}
