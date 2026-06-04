package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Append-only — only {@code append} is exposed; no update/delete path exists
 * (erp E2 / E4 / E8 + A3). Written in the SAME Tx as the state change + outbox
 * row (architecture.md § Outbox + audit_log invariants).
 */
@Component
@RequiredArgsConstructor
public class ApprovalAuditLogRepositoryImpl implements ApprovalAuditLogRepository {

    private final ApprovalAuditLogJpaRepository jpa;

    @Override
    public ApprovalAuditLog append(ApprovalAuditLog row) {
        return jpa.save(row);
    }
}
