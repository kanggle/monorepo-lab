package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-tenant FX cost-flow method config (PK = tenant_id). */
public interface FxCostFlowConfigJpaRepository
        extends JpaRepository<FxCostFlowConfig, String> {
}
