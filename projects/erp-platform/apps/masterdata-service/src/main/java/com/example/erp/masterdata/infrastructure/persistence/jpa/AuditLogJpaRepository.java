package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long> {
}
