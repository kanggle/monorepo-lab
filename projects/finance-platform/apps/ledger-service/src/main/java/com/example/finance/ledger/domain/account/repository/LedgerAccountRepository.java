package com.example.finance.ledger.domain.account.repository;

import com.example.finance.ledger.domain.account.LedgerAccount;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the chart of accounts (architecture.md § Layer Structure).
 * Implemented by an infrastructure JPA adapter; the application layer depends on
 * this interface only.
 */
public interface LedgerAccountRepository {

    Optional<LedgerAccount> findByCode(String code, String tenantId);

    boolean existsByCode(String code, String tenantId);

    LedgerAccount save(LedgerAccount account);

    List<LedgerAccount> findAll(String tenantId);
}
