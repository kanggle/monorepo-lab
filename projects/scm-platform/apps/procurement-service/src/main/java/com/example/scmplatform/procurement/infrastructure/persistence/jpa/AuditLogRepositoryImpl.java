package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpa;

    @Override
    public AuditLog save(AuditLog row) {
        return jpa.save(row);
    }
}
