package com.example.finance.ledger.domain.journal;

/**
 * The side a single {@link JournalLine} posts on. A balanced {@link JournalEntry}
 * has {@code Σ DEBIT == Σ CREDIT}. Pure Java.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT;

    /** The opposite side — used to swap debit/credit for a reversal entry (F3). */
    public EntryDirection opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
