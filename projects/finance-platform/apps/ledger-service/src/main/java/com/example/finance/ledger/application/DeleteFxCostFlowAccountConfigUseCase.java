package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Delete a per-account FX cost-flow method override (21st increment — TASK-FIN-BE-029).
 * One {@code @Transactional} boundary:
 *
 * <ol>
 *   <li>delete the override row for {@code (tenant_id, ledger_account_code)} — the account falls
 *       back to the per-tenant default;</li>
 *   <li>when a row actually existed, write an audit row {@code FX_COST_FLOW_ACCOUNT_METHOD_CLEARED}
 *       in the SAME transaction (regulated/audit-heavy).</li>
 * </ol>
 *
 * <p><b>Idempotent</b> — deleting a non-existent override is a no-op that returns
 * {@code cleared=false} and does NOT error or write an audit row (an operator may DELETE the same
 * code twice; do not 404). The audit row is written ONLY when a row was actually cleared.
 */
@Service
@RequiredArgsConstructor
public class DeleteFxCostFlowAccountConfigUseCase {

    private static final String AGGREGATE_TYPE = "FxCostFlowAccountConfig";

    private final FxCostFlowAccountConfigRepository fxCostFlowAccountConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClockPort clock;

    /**
     * Delete the override; returns whether a row existed (and was therefore cleared). When nothing
     * existed, no audit row is written (idempotent no-op).
     */
    @Transactional
    public boolean clear(String tenantId, String ledgerAccountCode, String actor) {
        boolean existed = fxCostFlowAccountConfigRepository
                .deleteByTenantIdAndAccountCode(tenantId, ledgerAccountCode);
        if (!existed) {
            return false;
        }

        Instant now = clock.now();
        auditLogRepository.save(AuditLog.of(
                tenantId, AGGREGATE_TYPE, tenantId + ":" + ledgerAccountCode,
                "FX_COST_FLOW_ACCOUNT_METHOD_CLEARED", actor,
                "account=" + ledgerAccountCode,
                "clear fx cost-flow account override", now));
        return true;
    }
}
