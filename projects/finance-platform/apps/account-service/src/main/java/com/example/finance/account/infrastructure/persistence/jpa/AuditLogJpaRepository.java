package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long> {
}
