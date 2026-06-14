package com.example.finance.ledger.domain.reconciliation.repository;

import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;

import java.util.Optional;

/**
 * Outbound port for the per-tenant FX reconciliation tolerance config (13th
 * increment — TASK-FIN-BE-020, architecture.md § FX reconciliation tolerance).
 * One row per tenant; an upsert is last-write-wins. <b>Absence → EXACT</b> (the
 * application treats an empty result as {@code FxTolerance.EXACT}). Implemented by
 * an infrastructure JPA adapter.
 */
public interface ReconciliationFxToleranceRepository {

    /** The tenant's tolerance config, or empty when unset (→ EXACT). */
    Optional<ReconciliationFxToleranceConfig> findByTenantId(String tenantId);

    /** Upsert the tenant's tolerance config (insert-or-update on the {@code tenant_id} PK). */
    ReconciliationFxToleranceConfig save(ReconciliationFxToleranceConfig config);
}
