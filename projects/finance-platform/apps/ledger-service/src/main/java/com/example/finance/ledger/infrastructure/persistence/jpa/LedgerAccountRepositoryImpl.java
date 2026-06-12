package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.account.LedgerAccount;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link LedgerAccountRepository}. */
@Component
@RequiredArgsConstructor
public class LedgerAccountRepositoryImpl implements LedgerAccountRepository {

    private final LedgerAccountJpaRepository jpa;

    @Override
    public Optional<LedgerAccount> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public boolean existsByCode(String code, String tenantId) {
        return jpa.existsByCodeAndTenantId(code, tenantId);
    }

    @Override
    public LedgerAccount save(LedgerAccount account) {
        return jpa.save(account);
    }

    @Override
    public List<LedgerAccount> findAll(String tenantId) {
        return jpa.findByTenantId(tenantId);
    }
}
