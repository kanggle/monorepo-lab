package com.example.finance.account.domain.audit;

/**
 * Outbound port for the append-only audit log (F6). Only {@code save} — there
 * is no update/delete capability by design.
 */
public interface AuditLogRepository {
    AuditLog save(AuditLog row);
}
