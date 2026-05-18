package com.example.finance.account.domain.transaction.repository;

import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.status.TransactionStatus;

import java.util.Optional;

/**
 * Outbound port for transaction persistence (Hexagonal). Tenant-scoped — no
 * tenant-omitting method (architecture.md § Multi-tenancy).
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(String id, String tenantId);

    Page findByAccountId(String accountId,
                         String tenantId,
                         TransactionType type,
                         TransactionStatus status,
                         int page,
                         int size);

    /** Minimal page projection — keeps Spring Data types out of the port. */
    record Page(java.util.List<Transaction> content,
                int page,
                int size,
                long totalElements,
                int totalPages) {
    }
}
