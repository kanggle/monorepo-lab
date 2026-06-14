package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfig;
import com.example.finance.ledger.domain.journal.FxCostFlowAccountConfigId;
import com.example.finance.ledger.domain.journal.repository.FxCostFlowAccountConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link FxCostFlowAccountConfigRepository}. */
@Component
@RequiredArgsConstructor
public class FxCostFlowAccountConfigRepositoryImpl implements FxCostFlowAccountConfigRepository {

    private final FxCostFlowAccountConfigJpaRepository jpa;

    @Override
    public Optional<FxCostFlowAccountConfig> findByTenantIdAndAccountCode(String tenantId,
                                                                          String ledgerAccountCode) {
        return jpa.findById(new FxCostFlowAccountConfigId(tenantId, ledgerAccountCode));
    }

    @Override
    public List<FxCostFlowAccountConfig> findByTenantId(String tenantId) {
        return jpa.findByTenantIdOrderByLedgerAccountCodeAsc(tenantId);
    }

    @Override
    public FxCostFlowAccountConfig save(FxCostFlowAccountConfig config) {
        return jpa.save(config);
    }

    @Override
    public boolean deleteByTenantIdAndAccountCode(String tenantId, String ledgerAccountCode) {
        FxCostFlowAccountConfigId id = new FxCostFlowAccountConfigId(tenantId, ledgerAccountCode);
        // Find-then-delete so the caller learns whether a row actually existed (the audit row is
        // only written when something was cleared — idempotent operator action).
        if (jpa.findById(id).isEmpty()) {
            return false;
        }
        jpa.deleteById(id);
        return true;
    }
}
