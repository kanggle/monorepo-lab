package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.journal.CompletedTransaction;

import java.util.Objects;

/**
 * Command driving {@link PostFromTransactionUseCase}: a parsed account-service
 * transaction event plus the dedupe/provenance keys (the signed source event id
 * + the topic). Built by the messaging adapter; the application layer never sees
 * Kafka/Jackson types.
 *
 * @param eventId         the signed envelope eventId (dedupe key, F1)
 * @param topic           the source topic (provenance)
 * @param reversal        true for a {@code reversed.v1} event (swap-original path)
 * @param reversalOfTransactionId  the ORIGINAL transaction id for a reversal
 * @param transaction     the parsed completed transaction (forward path)
 */
public record PostFromTransactionCommand(
        String eventId,
        String topic,
        boolean reversal,
        String reversalOfTransactionId,
        CompletedTransaction transaction) {

    public PostFromTransactionCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(transaction, "transaction");
    }

    /** Factory for a forward (completed.v1) command. */
    public static PostFromTransactionCommand completed(String eventId, String topic,
                                                       CompletedTransaction txn) {
        return new PostFromTransactionCommand(eventId, topic, false, null, txn);
    }

    /** Factory for a reversal (reversed.v1) command. */
    public static PostFromTransactionCommand reversed(String eventId, String topic,
                                                      String reversalOfTransactionId,
                                                      CompletedTransaction txn) {
        Objects.requireNonNull(reversalOfTransactionId, "reversalOfTransactionId");
        return new PostFromTransactionCommand(eventId, topic, true, reversalOfTransactionId, txn);
    }
}
