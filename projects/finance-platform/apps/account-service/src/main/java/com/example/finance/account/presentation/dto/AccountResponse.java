package com.example.finance.account.presentation.dto;

import com.example.finance.account.application.view.AccountView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Account read response. ownerRef intentionally omitted (regulated PII, F7). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(String accountId,
                              String status,
                              String currency,
                              String kycLevel,
                              List<BalanceResponse> balances,
                              Instant createdAt,
                              Instant updatedAt) {

    public static AccountResponse from(AccountView v) {
        return new AccountResponse(
                v.accountId(), v.status(), v.currency(), v.kycLevel(),
                v.balances() == null ? null
                        : v.balances().stream().map(b -> new BalanceResponse(
                        b.currency(), b.ledger(), b.available(), b.held())).toList(),
                v.createdAt(), v.updatedAt());
    }

    public record BalanceResponse(String currency, String ledger,
                                  String available, String held) {
    }
}
