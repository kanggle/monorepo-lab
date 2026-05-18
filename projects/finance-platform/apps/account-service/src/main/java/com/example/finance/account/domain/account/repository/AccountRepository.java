package com.example.finance.account.domain.account.repository;

import com.example.finance.account.domain.account.Account;

import java.util.Optional;

/**
 * Outbound port for account persistence (Hexagonal). Every lookup is
 * tenant-scoped — there is no tenant-omitting method (multi-tenancy
 * fail-closed; architecture.md § Multi-tenancy).
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(String id, String tenantId);
}
