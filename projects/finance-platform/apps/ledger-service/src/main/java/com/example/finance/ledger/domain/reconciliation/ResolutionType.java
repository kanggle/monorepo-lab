package com.example.finance.ledger.domain.reconciliation;

/**
 * How an operator resolved a {@link ReconciliationDiscrepancy} (architecture.md
 * § Reconciliation, fintech F8). {@code MATCHED_MANUALLY} — the operator linked
 * it to an internal entry by hand; {@code WRITTEN_OFF} — accepted as a loss
 * (e.g. a sub-threshold bank fee); {@code ACCEPTED} — accepted as a known
 * difference. None auto-adjusts a journal entry. Pure Java.
 */
public enum ResolutionType {
    MATCHED_MANUALLY,
    WRITTEN_OFF,
    ACCEPTED
}
