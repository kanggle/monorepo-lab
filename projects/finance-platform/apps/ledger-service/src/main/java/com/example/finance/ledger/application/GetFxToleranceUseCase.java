package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxToleranceView;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationFxToleranceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read the tenant's FX reconciliation tolerance (13th increment — TASK-FIN-BE-020).
 * Returns the persisted config, or the EXACT default {@code (0, 0)} when the tenant
 * has no configured row (the GET always surfaces the effective tolerance).
 * Tenant-scoped by the caller's {@code tenantId}.
 */
@Service
@RequiredArgsConstructor
public class GetFxToleranceUseCase {

    private final ReconciliationFxToleranceRepository fxToleranceRepository;

    @Transactional(readOnly = true)
    public FxToleranceView get(String tenantId) {
        return fxToleranceRepository.findByTenantId(tenantId)
                .map(FxToleranceView::from)
                .orElseGet(FxToleranceView::exact);
    }
}
