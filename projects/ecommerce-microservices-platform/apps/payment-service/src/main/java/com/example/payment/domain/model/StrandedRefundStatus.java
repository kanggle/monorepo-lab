package com.example.payment.domain.model;

/**
 * Lifecycle of a {@link StrandedRefund} record (TASK-BE-438, ADR-MONO-005 § 2.3 D3 Category A).
 *
 * <pre>
 *   STRANDED ──auto-resolved (PG already CANCELED, or re-cancel succeeds)──▶ RESOLVED   (terminal)
 *   STRANDED ──attempts &gt;= cap, or definitive 4xx PG reject───────────────▶ UNRESOLVED (terminal)
 * </pre>
 *
 * Both {@code RESOLVED} and {@code UNRESOLVED} are terminal; the sweeper's poll predicate
 * filters on {@code STRANDED} only, so a terminal record is never re-selected.
 */
public enum StrandedRefundStatus {
    /** Open obligation: captured funds not yet reversed; eligible for sweeper reconciliation. */
    STRANDED,
    /** Auto-healed: the PG was already cancelled, or a retry cancel succeeded. No operator action. */
    RESOLVED,
    /** Terminal failure: attempt cap exhausted or a definitive PG rejection. Re-escalated for an operator. */
    UNRESOLVED
}
