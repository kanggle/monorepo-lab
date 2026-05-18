package com.example.finance.account.domain.transaction.status;

/**
 * Transaction lifecycle (architecture.md § Transaction State Machine).
 *
 * <pre>
 * REQUESTED → VALIDATED → AUTHORIZED → SETTLED → COMPLETED
 *   (any pre-SETTLED) → FAILED
 *   COMPLETED → REVERSED (via a NEW reversal txn; original immutable)
 * </pre>
 *
 * {@code SETTLED}/{@code COMPLETED} are immutable (F3); FAILED/REVERSED are
 * terminal.
 */
public enum TransactionStatus {
    REQUESTED,
    VALIDATED,
    AUTHORIZED,
    SETTLED,
    COMPLETED,
    FAILED,
    REVERSED;

    /** F3: a settled/completed txn cannot be mutated in place. */
    public boolean isImmutable() {
        return this == SETTLED || this == COMPLETED || this == REVERSED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REVERSED;
    }
}
