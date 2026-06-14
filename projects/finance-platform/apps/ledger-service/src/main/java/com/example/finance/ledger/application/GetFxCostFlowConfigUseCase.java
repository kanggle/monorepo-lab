package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxCostFlowConfigView;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read the tenant's FX cost-flow method config (15th increment — TASK-FIN-BE-023).
 * Returns the persisted config, or the {@code WEIGHTED_AVERAGE} default when the tenant
 * has no configured row (the GET always surfaces the effective method).
 * Tenant-scoped by the caller's {@code tenantId}.
 */
@Service
@RequiredArgsConstructor
public class GetFxCostFlowConfigUseCase {

    private final FxCostFlowConfigRepository fxCostFlowConfigRepository;

    @Transactional(readOnly = true)
    public FxCostFlowConfigView get(String tenantId) {
        return fxCostFlowConfigRepository.findByTenantId(tenantId)
                .map(FxCostFlowConfigView::from)
                .orElseGet(FxCostFlowConfigView::weightedAverageDefault);
    }
}
