package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.money.Money;

import java.util.List;

/**
 * The trial balance (ledger-api.md § 4): every account's debit/credit totals +
 * the grand totals, which MUST be equal (Σ debit == Σ credit across the ledger).
 */
public record TrialBalanceView(
        List<AccountTotalsView> accounts,
        Money grandDebitTotal,
        Money grandCreditTotal,
        boolean inBalance) {

    /** Per-account totals row of the trial balance. */
    public record AccountTotalsView(String ledgerAccountCode, Money debitTotal, Money creditTotal) {
    }
}
