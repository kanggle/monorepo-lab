package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for recorded reconciliation matches (insert-only). */
public interface ReconciliationMatchJpaRepository
        extends JpaRepository<ReconciliationMatch, String> {

    /** Matches whose statement line belongs to the given statement (statement detail read). */
    List<ReconciliationMatch> findByTenantIdAndStatementLineIdIn(
            String tenantId, List<String> statementLineIds);
}
