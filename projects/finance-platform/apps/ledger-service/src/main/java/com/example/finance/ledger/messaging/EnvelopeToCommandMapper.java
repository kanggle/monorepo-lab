package com.example.finance.ledger.messaging;

import com.example.finance.ledger.application.PostFromTransactionCommand;
import com.example.finance.ledger.domain.journal.CompletedTransaction;
import com.example.finance.ledger.domain.journal.LedgerTransactionType;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps a raw Kafka record value (transaction envelope JSON) to a
 * {@link PostFromTransactionCommand}. All Kafka/Jackson types stay in this
 * adapter — the application layer receives a pure command (Hexagonal boundary).
 *
 * <ul>
 *   <li><b>completed.v1</b> — reads {@code type} / {@code accountId} /
 *       {@code money} / {@code counterpartyAccountId} from the payload; an
 *       unknown/absent transaction {@code type}, a missing {@code accountId}, or
 *       a malformed {@code money} → {@link InvalidEnvelopeException} → DLT.</li>
 *   <li><b>reversed.v1</b> — reads {@code reversalOfTransactionId} (required) +
 *       the reversal txn fields; the {@code type} is forced to {@code REVERSAL}.</li>
 * </ul>
 */
@Component
public class EnvelopeToCommandMapper {

    private final ObjectMapper objectMapper;

    public EnvelopeToCommandMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PostFromTransactionCommand mapCompleted(String rawValue, String topic) {
        TransactionEnvelope env = parse(rawValue, topic);
        CompletedTransaction txn = completedTransaction(env, topic);
        if (txn.type() == LedgerTransactionType.REVERSAL) {
            throw new InvalidEnvelopeException(
                    "REVERSAL must arrive on the reversed.v1 topic, not " + topic);
        }
        return PostFromTransactionCommand.completed(env.eventId(), topic, txn);
    }

    public PostFromTransactionCommand mapReversed(String rawValue, String topic) {
        TransactionEnvelope env = parse(rawValue, topic);
        String reversalOfTransactionId = env.payloadString("reversalOfTransactionId");
        if (reversalOfTransactionId == null || reversalOfTransactionId.isBlank()) {
            throw new InvalidEnvelopeException(
                    "reversed envelope missing reversalOfTransactionId on topic " + topic);
        }
        String transactionId = requireField(env, "transactionId", topic);
        String accountId = requireField(env, "accountId", topic);
        Money money = money(env, topic);
        CompletedTransaction txn = new CompletedTransaction(
                env.effectiveTenantId(), transactionId, accountId,
                LedgerTransactionType.REVERSAL, money, null);
        return PostFromTransactionCommand.reversed(
                env.eventId(), topic, reversalOfTransactionId, txn);
    }

    private CompletedTransaction completedTransaction(TransactionEnvelope env, String topic) {
        String transactionId = requireField(env, "transactionId", topic);
        String accountId = requireField(env, "accountId", topic);
        LedgerTransactionType type =
                LedgerTransactionType.fromOrNull(env.payloadString("type"));
        if (type == null) {
            throw new InvalidEnvelopeException(
                    "unknown/absent transaction type '" + env.payloadString("type")
                            + "' on topic " + topic);
        }
        Money money = money(env, topic);
        String counterparty = env.payloadString("counterpartyAccountId");
        return new CompletedTransaction(
                env.effectiveTenantId(), transactionId, accountId, type, money, counterparty);
    }

    private TransactionEnvelope parse(String rawValue, String topic) {
        TransactionEnvelope env;
        try {
            env = objectMapper.readValue(rawValue, TransactionEnvelope.class);
        } catch (Exception e) {
            throw new InvalidEnvelopeException(
                    "unparseable transaction envelope on topic " + topic + ": " + e.getMessage());
        }
        if (env == null || !env.isValid()) {
            throw new InvalidEnvelopeException(
                    "invalid transaction envelope (missing eventId/payload) on topic " + topic);
        }
        return env;
    }

    private static String requireField(TransactionEnvelope env, String key, String topic) {
        String v = env.payloadString(key);
        if (v == null || v.isBlank()) {
            throw new InvalidEnvelopeException(
                    "transaction envelope missing " + key + " on topic " + topic);
        }
        return v;
    }

    private Money money(TransactionEnvelope env, String topic) {
        Map<String, Object> moneyMap = env.moneyMap();
        if (moneyMap == null) {
            throw new InvalidEnvelopeException("transaction envelope missing money on topic " + topic);
        }
        Object amount = moneyMap.get("amount");
        Object currency = moneyMap.get("currency");
        if (amount == null || currency == null) {
            throw new InvalidEnvelopeException(
                    "transaction money missing amount/currency on topic " + topic);
        }
        try {
            return Money.of(amount.toString(), Currency.of(currency.toString()));
        } catch (RuntimeException e) {
            throw new InvalidEnvelopeException(
                    "malformed transaction money on topic " + topic + ": " + e.getMessage());
        }
    }
}
