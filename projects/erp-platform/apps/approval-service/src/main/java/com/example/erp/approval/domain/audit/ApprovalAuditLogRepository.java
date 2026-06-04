package com.example.erp.approval.domain.audit;

/**
 * Outbound port for the append-only approval audit log (E2 / E4 / E8 / A3).
 * Only {@code append} — there is no update/delete capability by design
 * (architecture.md § Outbox + audit_log invariants).
 */
public interface ApprovalAuditLogRepository {
    ApprovalAuditLog append(ApprovalAuditLog row);
}
