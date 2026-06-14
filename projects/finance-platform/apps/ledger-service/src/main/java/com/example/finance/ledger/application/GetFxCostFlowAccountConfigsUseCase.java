package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxCostFlowAccountConfigView;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * List the tenant's per-account FX cost-flow method overrides (21st increment —
 * TASK-FIN-BE-029). Returns the configured override rows ordered by {@code ledger_account_code}
 * ASC (empty list when the tenant has none) — accounts without an override row do NOT appear
 * (they inherit the per-tenant default resolved at settlement time). Tenant-scoped by the
 * caller's {@code tenantId}.
 */
@Service
@RequiredArgsConstructor
public class GetFxCostFlowAccountConfigsUseCase {

    private final FxCostFlowAccountConfigRepository fxCostFlowAccountConfigRepository;

    @Transactional(readOnly = true)
    public List<FxCostFlowAccountConfigView> list(String tenantId) {
        return fxCostFlowAccountConfigRepository.findByTenantId(tenantId).stream()
                .map(FxCostFlowAccountConfigView::from)
                .toList();
    }
}
