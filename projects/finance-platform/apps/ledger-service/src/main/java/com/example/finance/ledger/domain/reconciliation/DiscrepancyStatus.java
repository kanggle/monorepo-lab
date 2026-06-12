package com.example.finance.ledger.domain.reconciliation;

/**
 * The state of a {@link ReconciliationDiscrepancy} (architecture.md
 * § Reconciliation, fintech F8). A discrepancy is recorded {@code OPEN} and may
 * transition to {@code RESOLVED} <b>only</b> via the operator
 * {@code ResolveDiscrepancyUseCase} — there is no auto-close path. Pure Java.
 */
public enum DiscrepancyStatus {
    OPEN,
    RESOLVED
}
