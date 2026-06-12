package com.example.finance.ledger.domain.period;

import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;

import java.util.List;
import java.util.Objects;

/**
 * Close-time trial-balance snapshot of an {@link AccountingPeriod}
 * (architecture.md § Accounting Period § Close-time snapshot). Per-account
 * debit/credit totals over entries with {@code postedAt < to}, plus the grand
 * totals which MUST be equal ({@code Σ debit == Σ credit}) — the period's
 * immutable ending record (F3/F6 parity; insert-only). Equal to the live trial
 * balance at close.
 *
 * <p>PURE value object (a record, NOT a JPA entity) — money is integer minor
 * units only (F5). Built via {@link #of} which computes the grand totals + the
 * {@code inBalance} flag from the per-account rows.
 */
public record PeriodBalanceSnapshot(
        List<PeriodAccountTotal> accounts,
        Money grandDebitTotal,
        Money grandCreditTotal,
        boolean inBalance) {

    /**
     * Build a snapshot from the per-account totals, summing the grand totals and
     * deriving {@code inBalance}. {@code currency} is the snapshot's single
     * currency (first increment is single-currency); used to seed the grand-total
     * zero when {@code accounts} is empty (an empty period closes in balance).
     */
    public static PeriodBalanceSnapshot of(List<PeriodAccountTotal> accounts, Currency currency) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(currency, "currency");
        Money grandDebit = Money.zero(currency);
        Money grandCredit = Money.zero(currency);
        for (PeriodAccountTotal a : accounts) {
            grandDebit = grandDebit.add(a.debitTotal());
            grandCredit = grandCredit.add(a.creditTotal());
        }
        return new PeriodBalanceSnapshot(List.copyOf(accounts), grandDebit, grandCredit,
                grandDebit.equals(grandCredit));
    }
}
