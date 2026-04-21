package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.entity.AuditEventType;
import com.example.auth.domain.entity.AuditLog;
import com.example.auth.domain.repository.AuditLogRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, AuditLogRepository {

    long countByUserIdAndEventType(UUID userId, AuditEventType eventType);
}
