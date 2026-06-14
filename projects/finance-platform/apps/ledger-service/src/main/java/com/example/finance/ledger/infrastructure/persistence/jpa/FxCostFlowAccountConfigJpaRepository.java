package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for the per-account FX cost-flow method override (composite PK
 * {@code (tenant_id, ledger_account_code)} via {@link FxCostFlowAccountConfigId}).
 */
public interface FxCostFlowAccountConfigJpaRepository
        extends JpaRepository<FxCostFlowAccountConfig, FxCostFlowAccountConfigId> {

    /**
     * The tenant's account overrides ordered by {@code ledger_account_code} ASC. The {@code Id}
     * prefix navigates the composite-id {@code tenantId} component (an {@code @IdClass} field).
     */
    List<FxCostFlowAccountConfig> findByTenantIdOrderByLedgerAccountCodeAsc(String tenantId);
}
