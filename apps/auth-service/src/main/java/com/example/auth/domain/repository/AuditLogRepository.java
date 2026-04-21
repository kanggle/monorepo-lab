package com.example.auth.domain.repository;

import com.example.auth.domain.entity.AuditEventType;
import com.example.auth.domain.entity.AuditLog;

import java.util.UUID;

public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    long countByUserIdAndEventType(UUID userId, AuditEventType eventType);
}
