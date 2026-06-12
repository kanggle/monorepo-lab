package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.reconciliation.ExternalStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for statement lines (owned by the statement aggregate). */
public interface ReconciliationStatementLineJpaRepository
        extends JpaRepository<ExternalStatementLine, String> {

    List<ExternalStatementLine> findByStatementIdAndTenantId(String statementId, String tenantId);
}
