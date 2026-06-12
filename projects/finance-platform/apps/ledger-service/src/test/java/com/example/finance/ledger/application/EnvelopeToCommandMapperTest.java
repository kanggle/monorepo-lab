package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.journal.LedgerTransactionType;
import com.example.finance.ledger.messaging.EnvelopeToCommandMapper;
import com.example.finance.ledger.messaging.InvalidEnvelopeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeToCommandMapperTest {

    private static final String COMPLETED = "finance.transaction.completed.v1";
    private static final String REVERSED = "finance.transaction.reversed.v1";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final EnvelopeToCommandMapper mapper = new EnvelopeToCommandMapper(objectMapper);

    private static String completedEnvelope(String eventId, String type, String accountId,
                                            String counterparty) {
        String cp = counterparty == null ? "" : "\"counterpartyAccountId\":\"" + counterparty + "\",";
        return """
                {"eventId":"%s","eventType":"finance.transaction.completed","tenantId":"finance",
                 "source":"finance-platform-account-service","aggregateType":"transaction",
                 "aggregateId":"%s",
                 "payload":{"transactionId":"%s","accountId":"%s","type":"%s",%s
                   "money":{"amount":"150000","currency":"KRW"},"status":"COMPLETED"}}
                """.formatted(eventId, accountId, accountId, accountId, type, cp);
    }

    @Test
    @DisplayName("a well-formed completed envelope maps to a forward command")
    void mapCompleted() {
        var cmd = mapper.mapCompleted(
                completedEnvelope("evt-1", "TOPUP", "acc-1", null), COMPLETED);
        assertThat(cmd.eventId()).isEqualTo("evt-1");
        assertThat(cmd.reversal()).isFalse();
        assertThat(cmd.transaction().type()).isEqualTo(LedgerTransactionType.TOPUP);
        assertThat(cmd.transaction().tenantId()).isEqualTo("finance");
        assertThat(cmd.transaction().money().minorUnits()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("a TRANSFER envelope carries the counterparty account")
    void mapTransfer() {
        var cmd = mapper.mapCompleted(
                completedEnvelope("evt-1", "TRANSFER", "acc-A", "acc-B"), COMPLETED);
        assertThat(cmd.transaction().type()).isEqualTo(LedgerTransactionType.TRANSFER);
        assertThat(cmd.transaction().counterpartyAccountId()).isEqualTo("acc-B");
    }

    @Test
    @DisplayName("unparseable JSON → InvalidEnvelopeException (→ DLT)")
    void unparseable() {
        assertThatThrownBy(() -> mapper.mapCompleted("{not json", COMPLETED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    @DisplayName("missing eventId → InvalidEnvelopeException")
    void missingEventId() {
        String env = """
                {"eventType":"x","tenantId":"finance",
                 "payload":{"transactionId":"t","accountId":"a","type":"TOPUP",
                   "money":{"amount":"100","currency":"KRW"}}}
                """;
        assertThatThrownBy(() -> mapper.mapCompleted(env, COMPLETED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    @DisplayName("unknown transaction type → InvalidEnvelopeException")
    void unknownType() {
        assertThatThrownBy(() -> mapper.mapCompleted(
                completedEnvelope("evt-1", "FROBNICATE", "acc-1", null), COMPLETED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }

    @Test
    @DisplayName("a reversed envelope maps to a reversal command with the original txn id")
    void mapReversed() {
        String env = """
                {"eventId":"rev-1","eventType":"finance.transaction.reversed","tenantId":"finance",
                 "payload":{"transactionId":"rev-txn","reversalOfTransactionId":"orig-txn",
                   "accountId":"acc-1","money":{"amount":"150000","currency":"KRW"}}}
                """;
        var cmd = mapper.mapReversed(env, REVERSED);
        assertThat(cmd.reversal()).isTrue();
        assertThat(cmd.reversalOfTransactionId()).isEqualTo("orig-txn");
        assertThat(cmd.transaction().type()).isEqualTo(LedgerTransactionType.REVERSAL);
    }

    @Test
    @DisplayName("a reversed envelope missing reversalOfTransactionId → InvalidEnvelopeException")
    void reversedMissingOriginal() {
        String env = """
                {"eventId":"rev-1","tenantId":"finance",
                 "payload":{"transactionId":"rev-txn","accountId":"acc-1",
                   "money":{"amount":"150000","currency":"KRW"}}}
                """;
        assertThatThrownBy(() -> mapper.mapReversed(env, REVERSED))
                .isInstanceOf(InvalidEnvelopeException.class);
    }
}
