package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.view.FxCostFlowAccountConfigView;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.journal.CostFlowMethod;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert a per-account FX cost-flow method override (21st increment — TASK-FIN-BE-029).
 * One {@code @Transactional} boundary, mirroring {@link SetFxCostFlowConfigUseCase}:
 *
 * <ol>
 *   <li>validate the method string via {@link CostFlowMethod#fromString} BEFORE any persist
 *       — an unknown method (e.g. {@code "LIFO"}) throws
 *       {@code CostFlowMethodInvalidException} ({@code VALIDATION_ERROR}, 400) and nothing
 *       is written;</li>
 *   <li>upsert the per-account override row (last-write-wins on the composite
 *       {@code (tenant_id, ledger_account_code)} PK), stamping the audit fields
 *       {@code updated_by} (the {@link ActorContext} identity) / {@code updated_at} (the clock);</li>
 *   <li>write an audit row {@code FX_COST_FLOW_ACCOUNT_METHOD_SET} in the SAME transaction
 *       (regulated/audit-heavy — a partial commit is impossible). {@code aggregateId =
 *       tenantId + ":" + ledgerAccountCode}.</li>
 * </ol>
 *
 * <p>The override is read by {@code SettleForeignPositionUseCase}, which resolves the effective
 * method with the precedence {@code account override > tenant default > WEIGHTED_AVERAGE}. There is
 * NO validation that the account exists (parity with the per-tenant config, which is keyed only by
 * tenant — an operator may pre-configure a code).
 */
@Service
@RequiredArgsConstructor
public class SetFxCostFlowAccountConfigUseCase {

    private static final String AGGREGATE_TYPE = "FxCostFlowAccountConfig";

    private final FxCostFlowAccountConfigRepository fxCostFlowAccountConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    @Transactional
    public FxCostFlowAccountConfigView set(SetFxCostFlowAccountConfigCommand command) {
        // Validate BEFORE constructing the domain object — an unknown method string must
        // raise VALIDATION_ERROR (400) and write nothing (mirrors SetFxCostFlowConfigUseCase).
        CostFlowMethod method = CostFlowMethod.fromString(command.method());

        return AuditedUpsert.run(clock, auditLogRepository,
                now -> fxCostFlowAccountConfigRepository.save(
                        FxCostFlowAccountConfig.of(command.tenantId(), command.ledgerAccountCode(),
                                method, command.actor(), now)),
                (saved, now) -> AuditLog.of(
                        command.tenantId(), AGGREGATE_TYPE,
                        command.tenantId() + ":" + command.ledgerAccountCode(),
                        "FX_COST_FLOW_ACCOUNT_METHOD_SET", command.actor(),
                        "account=" + saved.ledgerAccountCode() + " method=" + saved.method().name(),
                        "set fx cost-flow account override", now),
                FxCostFlowAccountConfigView::from);
    }
}
