package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxCostFlowConfig;

import java.util.Optional;

/**
 * Outbound port for the per-tenant FX cost-flow method config (15th increment —
 * TASK-FIN-BE-023, architecture.md § FX cost-flow method config). One row per tenant;
 * an upsert is last-write-wins. <b>Absence → {@code WEIGHTED_AVERAGE}</b> (the
 * application treats an empty result as the weighted-average default — net-zero).
 * Implemented by an infrastructure JPA adapter.
 */
public interface FxCostFlowConfigRepository {

    /** The tenant's cost-flow config, or empty when unset (→ WEIGHTED_AVERAGE). */
    Optional<FxCostFlowConfig> findByTenantId(String tenantId);

    /** Upsert the tenant's cost-flow config (insert-or-update on the {@code tenant_id} PK). */
    FxCostFlowConfig save(FxCostFlowConfig config);
}
