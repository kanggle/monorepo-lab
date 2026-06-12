package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.TrialBalanceView;

import java.util.List;

/**
 * GET /trial-balance response (ledger-api.md § 4). (8th incr) each account carries
 * its base-currency (KRW) totals and the grand totals add the base-currency
 * consolidated {@code grandBaseDebitTotal == grandBaseCreditTotal}.
 */
public record TrialBalanceResponse(
        List<AccountTotalsResponse> accounts,
        MoneyResponse grandDebitTotal,
        MoneyResponse grandCreditTotal,
        MoneyResponse grandBaseDebitTotal,
        MoneyResponse grandBaseCreditTotal,
        boolean inBalance) {

    public record AccountTotalsResponse(String ledgerAccountCode,
                                        MoneyResponse debitTotal, MoneyResponse creditTotal,
                                        MoneyResponse baseDebitTotal, MoneyResponse baseCreditTotal) {
    }

    public static TrialBalanceResponse from(TrialBalanceView v) {
        List<AccountTotalsResponse> accounts = v.accounts().stream()
                .map(a -> new AccountTotalsResponse(a.ledgerAccountCode(),
                        MoneyResponse.from(a.debitTotal()), MoneyResponse.from(a.creditTotal()),
                        MoneyResponse.from(a.baseDebitTotal()), MoneyResponse.from(a.baseCreditTotal())))
                .toList();
        return new TrialBalanceResponse(accounts,
                MoneyResponse.from(v.grandDebitTotal()),
                MoneyResponse.from(v.grandCreditTotal()),
                MoneyResponse.from(v.grandBaseDebitTotal()),
                MoneyResponse.from(v.grandBaseCreditTotal()), v.inBalance());
    }
}
