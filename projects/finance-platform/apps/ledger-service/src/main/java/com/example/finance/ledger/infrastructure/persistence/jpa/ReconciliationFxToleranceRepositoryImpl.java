package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.ReconciliationFxToleranceConfig;
import com.example.finance.ledger.domain.reconciliation.repository.ReconciliationFxToleranceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** JPA adapter for {@link ReconciliationFxToleranceRepository}. */
@Component
@RequiredArgsConstructor
public class ReconciliationFxToleranceRepositoryImpl implements ReconciliationFxToleranceRepository {

    private final ReconciliationFxToleranceJpaRepository jpa;

    @Override
    public Optional<ReconciliationFxToleranceConfig> findByTenantId(String tenantId) {
        return jpa.findById(tenantId);
    }

    @Override
    public ReconciliationFxToleranceConfig save(ReconciliationFxToleranceConfig config) {
        return jpa.save(config);
    }
}
