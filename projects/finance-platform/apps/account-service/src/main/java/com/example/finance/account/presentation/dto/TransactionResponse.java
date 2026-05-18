package com.example.finance.account.presentation.dto;

import com.example.finance.account.application.view.TransactionView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(String transactionId,
                                  String type,
                                  String status,
                                  MoneyResponse money,
                                  String counterpartyAccountId,
                                  String reversalOfTransactionId,
                                  Instant createdAt,
                                  Instant settledAt) {

    public static TransactionResponse from(TransactionView v) {
        return new TransactionResponse(
                v.transactionId(), v.type(), v.status(),
                new MoneyResponse(v.amountMinor(), v.currency()),
                v.counterpartyAccountId(), v.reversalOfTransactionId(),
                v.createdAt(), v.settledAt());
    }
}
