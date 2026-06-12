package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.DiscrepancyView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationDiscrepancyNotFoundException;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ResolutionType;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
    private final ClockPort clock;

    @Transactional
    public DiscrepancyView resolve(String discrepancyId, String tenantId,
                                   ResolutionType resolutionType, String note, String actor) {
        ReconciliationDiscrepancy discrepancy =
                reconciliationRepository.findDiscrepancyById(discrepancyId, tenantId)
                        .orElseThrow(() -> new ReconciliationDiscrepancyNotFoundException(
                                "reconciliation discrepancy not found: " + discrepancyId));

        Instant now = clock.now();
        // resolve() throws ReconciliationAlreadyResolvedException if not OPEN.
        discrepancy.resolve(resolutionType, note, actor, now);

        ReconciliationDiscrepancy saved = reconciliationRepository.saveDiscrepancy(discrepancy);
        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, discrepancyId, "RESOLVED",
                actor, auditSummary(saved), "resolve reconciliation discrepancy", now));

        return DiscrepancyView.from(saved);
    }

    private static String auditSummary(ReconciliationDiscrepancy d) {
        return "discrepancyId=" + d.discrepancyId() + " type=" + d.type()
                + " status=" + d.status() + " resolutionType=" + d.resolutionType();
    }
}
