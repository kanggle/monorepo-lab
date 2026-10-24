package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.transaction.Transaction;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.SettledTransactionRow;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionJpaRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdAndTenantId(String id, String tenantId);

    @Query("""
            select t from Transaction t
             where t.accountId = :accountId
               and t.tenantId = :tenantId
               and (:type is null or t.type = :type)
               and (:status is null or t.status = :status)
             order by t.createdAt desc
            """)
    Page<Transaction> search(@Param("accountId") String accountId,
                             @Param("tenantId") String tenantId,
                             @Param("type") TransactionType type,
                             @Param("status") TransactionStatus status,
                             Pageable pageable);

    /**
     * Metrics (TASK-MONO-812): every transaction of the tenant settled in {@code [from, to)},
     * any type, any current status. Selects only the five columns the metrics need — never
     * {@code account_id} / {@code counterparty_account_id} / {@code hold_id} (AC-3). Served by
     * {@code idx_txn_tenant_settled} (V3).
     */
    @Query("""
            select new com.example.finance.account.domain.transaction.repository.SettledTransactionRow(
                       t.type, t.status, t.amountMinor, t.currency, t.settledAt)
              from Transaction t
             where t.tenantId = :tenantId
               and t.settledAt >= :from
               and t.settledAt < :to
            """)
    List<SettledTransactionRow> findSettledIn(@Param("tenantId") String tenantId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);
}
