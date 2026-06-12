package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.LedgerAccountBalanceView;

/** GET /accounts/{code}/balance response (ledger-api.md § 3). */
public record BalanceResponse(
        String ledgerAccountCode,
        String type,
        String normalSide,
        MoneyResponse debitTotal,
        MoneyResponse creditTotal,
        MoneyResponse balance,
        String balanceSide) {

    public static BalanceResponse from(LedgerAccountBalanceView v) {
        return new BalanceResponse(
                v.ledgerAccountCode(), v.type().name(), v.normalSide().name(),
                MoneyResponse.from(v.debitTotal()), MoneyResponse.from(v.creditTotal()),
                MoneyResponse.from(v.balance()), v.balanceSide().name());
    }
}
