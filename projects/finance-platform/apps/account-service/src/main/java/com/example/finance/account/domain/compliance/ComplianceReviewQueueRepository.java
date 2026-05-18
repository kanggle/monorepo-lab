package com.example.finance.account.domain.compliance;

/**
 * Outbound port for the operator review queue (F4/F8). Append-only by design
 * — v1 has no resolve/close capability (operator console = v2 admin-service).
 */
public interface ComplianceReviewQueueRepository {
    ComplianceReviewQueueEntry save(ComplianceReviewQueueEntry entry);
}
