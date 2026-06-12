package com.example.finance.ledger.domain.reconciliation;

/**
 * Whether an {@link ExternalStatementLine} was matched to an internal ledger
 * entry during reconciliation (architecture.md § Reconciliation). A line is
 * ingested {@code UNMATCHED}; the matcher flips it to {@code MATCHED} when it
 * pairs with an internal line. Pure Java.
 */
public enum LineMatchStatus {
    UNMATCHED,
    MATCHED
}
