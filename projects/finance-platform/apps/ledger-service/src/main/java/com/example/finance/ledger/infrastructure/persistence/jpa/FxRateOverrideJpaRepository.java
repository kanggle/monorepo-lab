package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.FxRateOverrideId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the per-tenant FX contract-rate override (28th increment —
 * TASK-FIN-BE-042). Composite PK = {@link FxRateOverrideId}
 * ({@code (tenant_id, base_currency, foreign_currency)}).
 */
public interface FxRateOverrideJpaRepository
        extends JpaRepository<FxRateOverride, FxRateOverrideId> {
}
