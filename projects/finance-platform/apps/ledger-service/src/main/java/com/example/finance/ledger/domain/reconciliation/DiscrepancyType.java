package com.example.finance.ledger.domain.reconciliation;

/**
 * The classification of a recorded {@link ReconciliationDiscrepancy}
 * (architecture.md § Reconciliation). {@code UNMATCHED_EXTERNAL} — an external
 * statement line with no internal counterpart; {@code UNMATCHED_INTERNAL} — an
 * internal ledger line with no external counterpart. {@code AMOUNT_MISMATCH} is
 * reserved for a later fuzzy-matching increment (the first increment classifies
 * only the two UNMATCHED_* cases — 1:1 by exact (amount, currency, direction)).
 * Pure Java.
 */
public enum DiscrepancyType {
    UNMATCHED_EXTERNAL,
    UNMATCHED_INTERNAL,
    AMOUNT_MISMATCH
}
