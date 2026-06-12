package com.example.finance.ledger.domain.journal;

/**
 * The account-service transaction kinds the Posting Policy maps
 * (finance-account-events.md — the full {@code TransactionType.name()} the
 * producer emits). {@code HOLD} / {@code RELEASE} change the wallet's
 * held/available split (single-entry, account-service) but NOT the confirmed
 * ledger balance, so they post no journal entry. Pure Java.
 */
public enum LedgerTransactionType {
    TOPUP,
    WITHDRAW,
    CAPTURE,
    TRANSFER,
    HOLD,
    RELEASE,
    REVERSAL;

    /** Resolve a producer type name to a known type, or {@code null} if unknown. */
    public static LedgerTransactionType fromOrNull(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True for the types that change the confirmed ledger balance (post an entry). */
    public boolean postsEntry() {
        return this == TOPUP || this == WITHDRAW || this == CAPTURE || this == TRANSFER;
    }
}
