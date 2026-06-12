package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the append-only audit log. */
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long> {
}
