package com.example.finance.account.domain.balance.repository;

import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for balance + hold persistence (Hexagonal). Tenant-scoped —
 * no tenant-omitting method (architecture.md § Multi-tenancy).
 */
public interface BalanceRepository {

    Balance save(Balance balance);

    Optional<Balance> findByAccountId(String accountId, String tenantId);

    List<Balance> findAllByAccountId(String accountId, String tenantId);

    Hold saveHold(Hold hold);

    Optional<Hold> findHoldById(String holdId, String tenantId);

    /** Active holds whose {@code expiresAt} is before {@code beforeEpoch} (sweep). */
    List<Hold> findActiveExpiredHolds(java.time.Instant beforeEpoch, int limit);
}
