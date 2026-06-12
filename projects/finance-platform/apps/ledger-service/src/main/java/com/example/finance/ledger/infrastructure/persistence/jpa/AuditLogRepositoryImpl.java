package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.audit.AuditLog;
import com.example.finance.ledger.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** JPA adapter for {@link AuditLogRepository} — insert-only (F6). */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLog save(AuditLog auditLog) {
        return jpa.save(auditLog);
    }
}
