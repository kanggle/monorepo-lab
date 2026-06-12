package com.example.finance.ledger.presentation.dto;

/**
 * POST /reconciliation/discrepancies/{id}/resolve request (reconciliation-api.md
 * § 2). {@code resolutionType} ∈ {MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED}; the
 * controller resolves it to the domain enum.
 */
public record ResolveDiscrepancyRequest(String resolutionType, String note) {
}
