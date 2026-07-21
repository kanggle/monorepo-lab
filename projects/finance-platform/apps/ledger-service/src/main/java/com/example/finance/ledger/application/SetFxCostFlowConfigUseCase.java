package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert the tenant's FX cost-flow method config (15th increment — TASK-FIN-BE-023).
 * One {@code @Transactional} boundary:
 *
 * <ol>
 *   <li>validate the method string via {@link CostFlowMethod#fromString} BEFORE any persist
 *       — an unknown method (e.g. {@code "LIFO"}) throws
 *       {@code CostFlowMethodInvalidException} ({@code VALIDATION_ERROR}, 400) and nothing
 *       is written;</li>
 *   <li>upsert the per-tenant config row (last-write-wins on the {@code tenant_id} PK),
 *       stamping the audit fields {@code updated_by} (the {@link ActorContext} identity)
 *       / {@code updated_at} (the clock);</li>
 *   <li>write an audit row in the SAME transaction (regulated/audit-heavy — a partial
 *       commit is impossible).</li>
 * </ol>
 *
 * <p><b>Net-zero / shadow</b>: this only persists a config; {@code SettleForeignPositionUseCase}
 * / {@code FxSettlementPolicy} are <b>not modified</b> — settlement still computes
 * weighted-average regardless of the stored method. FIN-BE-025 wires FIFO consumption.
 */
@Service
@RequiredArgsConstructor
public class SetFxCostFlowConfigUseCase {

    private static final String AGGREGATE_TYPE = "FxCostFlowConfig";

    private final FxCostFlowConfigRepository fxCostFlowConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    @Transactional
    public FxCostFlowConfigView set(SetFxCostFlowConfigCommand command) {
        // Validate BEFORE constructing the domain object — an unknown method string must
        // raise VALIDATION_ERROR (400) and write nothing (mirrors SetFxToleranceUseCase).
        CostFlowMethod method = CostFlowMethod.fromString(command.method());

        return AuditedUpsert.run(clock, auditLogRepository,
                now -> fxCostFlowConfigRepository.save(
                        FxCostFlowConfig.of(command.tenantId(), method, command.actor(), now)),
                (saved, now) -> AuditLog.of(
                        command.tenantId(), AGGREGATE_TYPE, command.tenantId(),
                        "FX_COST_FLOW_METHOD_SET", command.actor(),
                        "method=" + saved.method().name(),
                        "set fx cost-flow method", now),
                FxCostFlowConfigView::from);
    }
}
