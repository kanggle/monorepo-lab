package com.example.finance.account.application.view;

import com.example.finance.account.domain.transaction.Transaction;

import java.time.Instant;

/** Read model for a transaction. Money as minor-unit strings (F5). */
public record TransactionView(String transactionId,
                              String type,
                              String status,
                              String amountMinor,
                              String currency,
                              String counterpartyAccountId,
                              String reversalOfTransactionId,
                              Instant createdAt,
                              Instant settledAt) {

    public static TransactionView from(Transaction t) {
        return new TransactionView(
                t.getId(),
                t.getType().name(),
                t.getStatus().name(),
                t.money().toMinorString(),
                t.getCurrency().code(),
                t.getCounterpartyAccountId(),
                t.getReversalOfTransactionId(),
                t.getCreatedAt(),
                t.getSettledAt());
    }
}
