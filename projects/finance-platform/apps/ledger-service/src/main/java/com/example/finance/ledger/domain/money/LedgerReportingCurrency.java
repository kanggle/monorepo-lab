package com.example.finance.ledger.domain.money;

/**
 * The ledger's fixed reporting / base currency (8th increment, TASK-FIN-BE-014 —
 * architecture.md § Multi-currency journals). A single journal entry may carry
 * lines in different {@link Currency} values, but the double-entry identity holds
 * in this base currency ({@code Σ baseDebit == Σ baseCredit}). Every
 * {@link com.example.finance.ledger.domain.journal.JournalLine} records its value
 * in this base currency ({@code baseAmount}) alongside its transaction money.
 *
 * <p>v1 fixes the base to {@link Currency#KRW}; a configurable base currency is
 * forward-declared (§ Increment Scope — OUT). Pure Java — no Spring/JPA.
 */
public final class LedgerReportingCurrency {

    /** The fixed reporting/base currency (KRW in v1). */
    public static final Currency BASE = Currency.KRW;

    private LedgerReportingCurrency() {
    }
}
