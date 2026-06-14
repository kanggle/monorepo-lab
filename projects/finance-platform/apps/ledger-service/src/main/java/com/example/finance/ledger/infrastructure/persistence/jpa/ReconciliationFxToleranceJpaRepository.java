package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the per-tenant FX reconciliation tolerance config (PK = tenant_id). */
public interface ReconciliationFxToleranceJpaRepository
        extends JpaRepository<ReconciliationFxToleranceConfig, String> {
}
