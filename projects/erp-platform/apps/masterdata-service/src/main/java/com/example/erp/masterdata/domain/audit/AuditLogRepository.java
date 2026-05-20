package com.example.erp.masterdata.domain.audit;

/**
 * Outbound port for the append-only audit log (E2 / E8). Only {@code append} —
 * there is no update/delete capability by design.
 */
public interface AuditLogRepository {
    AuditLog append(AuditLog row);
}
