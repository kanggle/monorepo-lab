package com.example.finance.ledger.domain.audit;

/**
 * Outbound port for the append-only audit log (F6). Implemented by an
 * infrastructure JPA adapter that only ever inserts.
 */
public interface AuditLogRepository {
    AuditLog save(AuditLog auditLog);
}
