package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.DiscrepancyStatus;
import com.example.finance.ledger.domain.reconciliation.ReconciliationDiscrepancy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for recorded reconciliation discrepancies (OPEN→RESOLVED). */
public interface ReconciliationDiscrepancyJpaRepository
        extends JpaRepository<ReconciliationDiscrepancy, String> {

    Optional<ReconciliationDiscrepancy> findByDiscrepancyIdAndTenantId(
            String discrepancyId, String tenantId);

    List<ReconciliationDiscrepancy> findByStatementIdAndTenantId(String statementId, String tenantId);

    /** The review queue, all statuses, most-recent first, paginated. */
    Page<ReconciliationDiscrepancy> findByTenantIdOrderByDetectedAtDescDiscrepancyIdDesc(
            String tenantId, Pageable pageable);

    /** The review queue, filtered by status, most-recent first, paginated. */
    Page<ReconciliationDiscrepancy> findByTenantIdAndStatusOrderByDetectedAtDescDiscrepancyIdDesc(
            String tenantId, DiscrepancyStatus status, Pageable pageable);
}
