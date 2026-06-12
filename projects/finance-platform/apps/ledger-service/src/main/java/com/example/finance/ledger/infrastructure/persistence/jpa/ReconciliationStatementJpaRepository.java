package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.ExternalStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for external statements (aggregate root + cascaded lines). */
public interface ReconciliationStatementJpaRepository
        extends JpaRepository<ExternalStatement, String> {

    Optional<ExternalStatement> findByStatementIdAndTenantId(String statementId, String tenantId);
}
