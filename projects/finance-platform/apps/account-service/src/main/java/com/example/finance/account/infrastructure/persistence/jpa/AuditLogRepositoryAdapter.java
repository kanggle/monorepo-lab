package com.example.finance.account.infrastructure.persistence.jpa;

import com.example.finance.account.domain.audit.AuditLog;
import com.example.finance.account.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Append-only — only {@code save}; no update/delete path exists (F6). */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLog save(AuditLog row) {
        return jpa.save(row);
    }
}
