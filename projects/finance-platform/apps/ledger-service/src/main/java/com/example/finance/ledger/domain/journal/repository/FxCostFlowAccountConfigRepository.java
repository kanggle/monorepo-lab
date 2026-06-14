package com.example.finance.ledger.domain.journal.repository;

import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the per-ACCOUNT FX cost-flow method override (21st increment —
 * TASK-FIN-BE-029, architecture.md § FX cost-flow method config / Per-account override). One row
 * per {@code (tenant_id, ledger_account_code)}; an upsert is last-write-wins. <b>Absence → fall
 * through to the per-tenant config, else {@code WEIGHTED_AVERAGE}</b> (the settlement use case
 * treats an empty account lookup as the net-zero fall-through). Row-level isolated by
 * {@code tenant_id} — tenant A's override for a code is invisible to tenant B. Implemented by an
 * infrastructure JPA adapter.
 */
public interface FxCostFlowAccountConfigRepository {

    /**
     * The account's cost-flow override for the tenant, or empty when unset (→ fall through to the
     * per-tenant config, else WEIGHTED_AVERAGE).
     */
    Optional<FxCostFlowAccountConfig> findByTenantIdAndAccountCode(String tenantId,
                                                                   String ledgerAccountCode);

    /** The tenant's account overrides, ordered by {@code ledger_account_code} ASC (empty when none). */
    List<FxCostFlowAccountConfig> findByTenantId(String tenantId);

    /** Upsert an account override (insert-or-update on the {@code (tenant_id, ledger_account_code)} PK). */
    FxCostFlowAccountConfig save(FxCostFlowAccountConfig config);

    /**
     * Delete the account override, returning whether a row existed (idempotent — deleting a
     * non-existent override returns {@code false} and writes nothing).
     */
    boolean deleteByTenantIdAndAccountCode(String tenantId, String ledgerAccountCode);
}
