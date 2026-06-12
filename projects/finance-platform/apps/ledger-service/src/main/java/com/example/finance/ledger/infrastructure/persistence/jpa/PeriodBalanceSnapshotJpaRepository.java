package com.example.finance.ledger.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for close-time period snapshot rows (insert-only). */
public interface PeriodBalanceSnapshotJpaRepository
        extends JpaRepository<PeriodBalanceSnapshotJpaEntity, Long> {

    List<PeriodBalanceSnapshotJpaEntity> findByPeriodIdAndTenantIdOrderByLedgerAccountCode(
            String periodId, String tenantId);
}
