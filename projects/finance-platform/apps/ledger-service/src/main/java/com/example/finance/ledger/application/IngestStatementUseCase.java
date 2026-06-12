package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.application.view.StatementView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAccountInvalidException;
import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import com.example.finance.ledger.domain.reconciliation.InternalLine;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import com.example.finance.ledger.domain.reconciliation.ReconciliationMatcher;
import com.example.finance.ledger.domain.reconciliation.ReconciliationResult;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationAccounts;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Ingests an external statement and runs reconciliation matching (architecture.md
 * § Reconciliation, fintech F8). One {@code @Transactional} boundary:
 *
 * <ol>
 *   <li>validate the target is a reconcilable clearing account (else
 *       {@code RECONCILIATION_ACCOUNT_INVALID});</li>
 *   <li>persist the statement + its lines;</li>
 *   <li>fetch the unmatched internal ledger lines on that account;</li>
 *   <li>run the pure {@link ReconciliationMatcher} (1:1 by amount/currency/
 *       direction);</li>
 *   <li>persist the matches (and the matched lines' flipped {@code matchStatus},
 *       cascaded with the statement re-save) + the <b>OPEN</b> discrepancies +
 *       an audit row;</li>
 *   <li>append the outbox events in THIS transaction —
 *       {@code reconciliation.completed} + one {@code discrepancy.detected} per
 *       discrepancy (transactional outbox, FIN-BE-009).</li>
 * </ol>
 *
 * <p><b>F8 — no auto-close.</b> Discrepancies are persisted OPEN and never
 * resolved or adjusted here; the only resolve path is {@link ResolveDiscrepancyUseCase}.
 * The use case never posts a balancing journal entry.
 */
@Service
@RequiredArgsConstructor
public class IngestStatementUseCase {

    private static final String AGGREGATE_TYPE = "ReconciliationStatement";

    private final ReconciliationRepository reconciliationRepository;
    private final AuditLogRepository auditLogRepository;
    private final LedgerEventPublisher ledgerEventPublisher;
    private final ClockPort clock;

    @Transactional
    public StatementView ingest(IngestStatementCommand command) {
        String tenantId = command.tenantId();
        String code = command.ledgerAccountCode();
        if (!ReconciliationAccounts.isReconcilable(code)) {
            throw new ReconciliationAccountInvalidException(
                    "ledger account is not a reconcilable clearing account: " + code);
        }

        Instant now = clock.now();
        List<ExternalStatement.RawLine> rawLines = command.lines().stream()
                .map(l -> new ExternalStatement.RawLine(
                        l.externalRef(), l.money(), l.direction(), l.valueDate(), l.description()))
                .toList();
        ExternalStatement statement = ExternalStatement.open(
                null, tenantId, code, command.source(), command.statementDate(), now, rawLines);
        // Persist the statement + its lines first so the line ids exist on the
        // matches/discrepancies and the matcher can flip matchStatus.
        ExternalStatement saved = reconciliationRepository.saveStatement(statement);

        List<InternalLine> internalLines =
                reconciliationRepository.findUnmatchedInternalLines(tenantId, code);

        ReconciliationResult result = ReconciliationMatcher.match(
                tenantId, saved.statementId(), code, saved.lines(), internalLines, now);

        // Re-save the statement so the matched lines' flipped matchStatus persists
        // (cascade), then the matches + OPEN discrepancies.
        reconciliationRepository.saveStatement(saved);
        reconciliationRepository.saveMatches(result.matches());
        List<ReconciliationDiscrepancy> discrepancies =
                reconciliationRepository.saveDiscrepancies(result.discrepancies());

        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, saved.statementId(), "INGESTED",
                command.actor(), auditSummary(saved, result),
                "ingest external statement + reconcile", now));

        // Append the GL/reconciliation-ops feed rows in THIS transaction (atomic with
        // the persisted matches/discrepancies — the feed cannot diverge from the books).
        ledgerEventPublisher.publishReconciliationCompleted(
                saved, result.matchedCount(), result.discrepancyCount());
        for (ReconciliationDiscrepancy discrepancy : discrepancies) {
            ledgerEventPublisher.publishDiscrepancyDetected(discrepancy);
        }

        return StatementView.of(saved, result.matches(), discrepancies);
    }

    private static String auditSummary(ExternalStatement s, ReconciliationResult result) {
        return "statementId=" + s.statementId() + " account=" + s.ledgerAccountCode()
                + " source=" + s.source() + " lines=" + s.lines().size()
                + " matched=" + result.matchedCount()
                + " discrepancies=" + result.discrepancyCount();
    }
}
