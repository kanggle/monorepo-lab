package com.example.finance.ledger.domain.reconciliation;

/**
 * The classification of a recorded {@link ReconciliationDiscrepancy}
 * (architecture.md § Reconciliation). {@code UNMATCHED_EXTERNAL} — an external
 * statement line with no internal counterpart; {@code UNMATCHED_INTERNAL} — an
 * internal ledger line with no external counterpart. {@code AMOUNT_MISMATCH} —
 * (11th incr — TASK-FIN-BE-017, multi-currency reconciliation) a foreign-currency
 * line that matched on the transaction (foreign) leg but whose bank-reported base
 * (KRW) value differs from the internal line's carrying base: an FX difference on
 * an otherwise-matched line, recorded for operator review (F8 — never
 * auto-adjusted). Pure Java.
 */
public enum DiscrepancyType {
    UNMATCHED_EXTERNAL,
    UNMATCHED_INTERNAL,
    AMOUNT_MISMATCH
}
