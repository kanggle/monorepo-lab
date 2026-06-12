package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.TrialBalanceView;

import java.util.List;

/** GET /trial-balance response (ledger-api.md § 4). */
public record TrialBalanceResponse(
        List<AccountTotalsResponse> accounts,
        MoneyResponse grandDebitTotal,
        MoneyResponse grandCreditTotal,
        boolean inBalance) {

    public record AccountTotalsResponse(String ledgerAccountCode,
                                        MoneyResponse debitTotal, MoneyResponse creditTotal) {
    }

    public static TrialBalanceResponse from(TrialBalanceView v) {
        List<AccountTotalsResponse> accounts = v.accounts().stream()
                .map(a -> new AccountTotalsResponse(a.ledgerAccountCode(),
                        MoneyResponse.from(a.debitTotal()), MoneyResponse.from(a.creditTotal())))
                .toList();
        return new TrialBalanceResponse(accounts,
                MoneyResponse.from(v.grandDebitTotal()),
                MoneyResponse.from(v.grandCreditTotal()), v.inBalance());
    }
}
