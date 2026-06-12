package com.example.finance.ledger.application.view;

import com.example.finance.ledger.domain.account.LedgerAccountType;
import com.example.finance.ledger.domain.account.NormalSide;
import com.example.finance.ledger.domain.money.Money;

/** A ledger account's running balance (ledger-api.md § 3). */
public record LedgerAccountBalanceView(
        String ledgerAccountCode,
        LedgerAccountType type,
        NormalSide normalSide,
        Money debitTotal,
        Money creditTotal,
        Money balance,
        NormalSide balanceSide) {
}
