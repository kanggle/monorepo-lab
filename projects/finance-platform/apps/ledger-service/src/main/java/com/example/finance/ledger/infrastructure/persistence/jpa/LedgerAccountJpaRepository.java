package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.account.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for the chart of accounts ({@code ledger_account}). */
public interface LedgerAccountJpaRepository extends JpaRepository<LedgerAccount, String> {

    Optional<LedgerAccount> findByCodeAndTenantId(String code, String tenantId);

    boolean existsByCodeAndTenantId(String code, String tenantId);

    List<LedgerAccount> findByTenantId(String tenantId);
}
