package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalAuditLogJpaRepository extends JpaRepository<ApprovalAuditLog, Long> {
}
