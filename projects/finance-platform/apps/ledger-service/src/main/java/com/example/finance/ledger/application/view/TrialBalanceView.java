package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.money.Money;

import java.util.List;

/**
 * The trial balance (ledger-api.md § 4): every account's debit/credit totals +
 * the grand totals, which MUST be equal (Σ debit == Σ credit across the ledger).
 *
 * <p>(8th incr) Each account also carries its base-currency (KRW) totals, and the
 * view adds the base-currency consolidated grand totals
 * ({@code grandBaseDebitTotal == grandBaseCreditTotal}) — the invariant that holds
 * across currencies. {@code inBalance} reflects the base-currency consolidation. In
 * the all-KRW happy path the original and base totals coincide; the per-account rows
 * keep the per-currency original breakdown.
 */
public record TrialBalanceView(
        List<AccountTotalsView> accounts,
        Money grandDebitTotal,
        Money grandCreditTotal,
        Money grandBaseDebitTotal,
        Money grandBaseCreditTotal,
        boolean inBalance) {

    /** Per-account totals row of the trial balance (original currency + base/KRW). */
    public record AccountTotalsView(String ledgerAccountCode,
                                    Money debitTotal, Money creditTotal,
                                    Money baseDebitTotal, Money baseCreditTotal) {
    }
}
