package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxCostFlowConfig;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** JPA adapter for {@link FxCostFlowConfigRepository}. */
@Component
@RequiredArgsConstructor
public class FxCostFlowConfigRepositoryImpl implements FxCostFlowConfigRepository {

    private final FxCostFlowConfigJpaRepository jpa;

    @Override
    public Optional<FxCostFlowConfig> findByTenantId(String tenantId) {
        return jpa.findById(tenantId);
    }

    @Override
    public FxCostFlowConfig save(FxCostFlowConfig config) {
        return jpa.save(config);
    }
}
