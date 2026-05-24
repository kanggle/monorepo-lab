package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.audit.AuditLog;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Append-only — only {@code append} is exposed; no update/delete path exists
 * (erp E2 / E8). The {@code audit_log} table also carries no UPDATE/DELETE
 * grant at the application layer (architecture.md § Outbox + audit_log
 * invariants).
 */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLog append(AuditLog row) {
        return jpa.save(row);
    }
}
