package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.money.Money;

import java.util.Objects;

/**
 * A parsed, completed account-service transaction — the pure input to
 * {@link PostingPolicy#toEntry}. Built by the messaging adapter from the
 * {@code finance.transaction.completed.v1} envelope (no Kafka/Jackson types reach
 * the domain). Pure Java value object.
 *
 * @param tenantId               always {@code finance} in v1 (from the envelope)
 * @param transactionId          the account-service transaction id (source ref)
 * @param accountId              the wallet account the transaction belongs to
 * @param type                   the transaction type (drives the policy)
 * @param money                  the amount (integer minor units, F5)
 * @param counterpartyAccountId  the other wallet for a TRANSFER (else {@code null})
 */
public record CompletedTransaction(
        String tenantId,
        String transactionId,
        String accountId,
        LedgerTransactionType type,
        Money money,
        String counterpartyAccountId) {

    public CompletedTransaction {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(money, "money");
    }
}
