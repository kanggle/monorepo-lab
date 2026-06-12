package com.example.finance.ledger.domain.period;

import com.example.finance.ledger.domain.money.Money;

/**
 * One ledger account's debit/credit totals captured in a
 * {@link PeriodBalanceSnapshot} at close time (architecture.md § Accounting
 * Period). Pure value object — money is integer minor units only (F5).
 */
public record PeriodAccountTotal(String ledgerAccountCode, Money debitTotal, Money creditTotal) {
}
