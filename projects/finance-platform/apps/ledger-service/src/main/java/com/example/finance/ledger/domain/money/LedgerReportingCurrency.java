package com.example.finance.ledger.domain.money;

import com.example.finance.common.money.Currency;

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
 *
 * <p>This type deliberately stays in {@code ledger-service} while {@link Currency}
 * itself moved to the shared {@code finance-common} module (ADR-003 Option A,
 * TASK-FIN-BE-064): <b>which</b> currency the ledger reports in is a ledger-owned
 * decision, not shared finance vocabulary — {@code platform/shared-library-policy.md}
 * § Ownership Rule.
 */
public final class LedgerReportingCurrency {

    /** The fixed reporting/base currency (KRW in v1). */
    public static final Currency BASE = Currency.KRW;

    private LedgerReportingCurrency() {
    }
}
