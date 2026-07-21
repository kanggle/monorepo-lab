package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxToleranceView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.FxToleranceInvalidException;
import com.example.finance.ledger.domain.reconciliation.FxTolerance;
import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationFxToleranceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert the tenant's FX reconciliation tolerance (13th increment — TASK-FIN-BE-020).
 * One {@code @Transactional} boundary:
 *
 * <ol>
 *   <li>validate {@code toleranceBps >= 0} and {@code floorMinor >= 0} (else
 *       {@code VALIDATION_ERROR}; the DB CHECK is the structural backstop) — runs
 *       before any persist so an invalid upsert writes nothing;</li>
 *   <li>upsert the per-tenant config row (last-write-wins on the {@code tenant_id} PK),
 *       stamping the audit fields {@code updated_by} (the {@code ActorContext} identity)
 *       / {@code updated_at} (the clock);</li>
 *   <li>write an audit row in the SAME transaction (regulated/audit-heavy — a partial
 *       commit is impossible).</li>
 * </ol>
 *
 * <p><b>F8 invariant is untouched</b> — this only persists a config; the tolerance is
 * consulted at ingest time and only ever <b>suppresses</b> the base-leg discrepancy.
 * It never auto-posts a correction or mutates a journal entry.
 */
@Service
@RequiredArgsConstructor
public class SetFxToleranceUseCase {

    private static final String AGGREGATE_TYPE = "ReconciliationFxTolerance";

    private final ReconciliationFxToleranceRepository fxToleranceRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    @Transactional
    public FxToleranceView set(SetFxToleranceCommand command) {
        // Validate BEFORE constructing the value object (a negative input would throw a
        // bare IllegalArgumentException there; we want the contract VALIDATION_ERROR).
        if (command.toleranceBps() < 0) {
            throw new FxToleranceInvalidException(
                    "toleranceBps must be >= 0: " + command.toleranceBps());
        }
        if (command.floorMinor() < 0) {
            throw new FxToleranceInvalidException(
                    "floorMinor must be >= 0: " + command.floorMinor());
        }

        FxTolerance tolerance = new FxTolerance(command.toleranceBps(), command.floorMinor());
        return AuditedUpsert.run(clock, auditLogRepository,
                now -> fxToleranceRepository.save(
                        ReconciliationFxToleranceConfig.of(
                                command.tenantId(), tolerance, command.actor(), now)),
                (saved, now) -> AuditLog.of(
                        command.tenantId(), AGGREGATE_TYPE, command.tenantId(), "FX_TOLERANCE_SET",
                        command.actor(),
                        "toleranceBps=" + saved.toleranceBps() + " floorMinor=" + saved.floorMinor(),
                        "set reconciliation fx tolerance", now),
                FxToleranceView::from);
    }
}
