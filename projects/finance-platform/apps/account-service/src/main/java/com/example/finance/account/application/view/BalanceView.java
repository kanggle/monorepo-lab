package com.example.finance.account.application.view;

import com.example.finance.account.domain.balance.Balance;

/** Read model for a single-currency balance. Money as minor-unit strings (F5). */
public record BalanceView(String currency,
                          String ledger,
                          String available,
                          String held) {

    public static BalanceView from(Balance b) {
        return new BalanceView(
                b.getCurrency().code(),
                b.ledger().toMinorString(),
                b.available().toMinorString(),
                b.held().toMinorString());
    }
}
