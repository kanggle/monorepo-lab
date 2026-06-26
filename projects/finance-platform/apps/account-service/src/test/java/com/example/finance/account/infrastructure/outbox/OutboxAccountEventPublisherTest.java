package com.example.finance.account.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.finance.account.application.event.AccountEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for {@link OutboxAccountEventPublisher} (TASK-FIN-BE-045 — outbox v2).
 *
 * <p>Pins the behaviour that MUST be preserved across the v1 → v2 migration: the
 * write path persists an {@code account_outbox} row whose payload is the EXACT
 * 7-field {@code BaseEventPublisher} envelope ({@code eventId, eventType, source,
 * occurredAt, schemaVersion, partitionKey, payload}) — so {@code ledger-service}
 * and other {@code finance.*} consumers (dedupe on {@code eventId}) are unaffected
 * (AC-2 / F2). {@code publishSanctionHit} (strings only) exercises the envelope
 * writer deterministically; the per-event payload builders + the full
 * persist→Kafka round-trip are covered by {@code AccountOutboxRelayIntegrationTest}.
 */
class OutboxAccountEventPublisherTest {

    private final AccountOutboxJpaRepository repository = mock(AccountOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-27T00:00:00Z"), ZoneOffset.UTC);
    private final OutboxAccountEventPublisher publisher =
            new OutboxAccountEventPublisher(repository, objectMapper, clock);

    @Test
    void publishSanctionHit_persistsV1EnvelopeShapedRow() throws Exception {
        when(repository.save(any(AccountOutboxJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        publisher.publishSanctionHit("acc-1", "txn-9", "screen-ref-7", "review-3");

        ArgumentCaptor<AccountOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(AccountOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        AccountOutboxJpaEntity row = captor.getValue();

        // Row metadata (AC-5).
        assertThat(row.getId()).isNotNull();
        assertThat(row.getAggregateType()).isEqualTo("account");
        assertThat(row.getAggregateId()).isEqualTo("acc-1");
        assertThat(row.getEventType()).isEqualTo(AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT);
        assertThat(row.getEventVersion()).isEqualTo("v1");
        assertThat(row.getPartitionKey()).isEqualTo("acc-1");
        assertThat(row.getOccurredAt()).isEqualTo(clock.instant());
        assertThat(row.getPublishedAt()).isNull();

        // Payload = the exact 7-field v1 envelope (AC-2 — wire preserved).
        JsonNode env = objectMapper.readTree(row.getPayload());
        assertThat(env.fieldNames()).toIterable().containsExactly(
                "eventId", "eventType", "source", "occurredAt", "schemaVersion",
                "partitionKey", "payload");
        // eventId in the envelope == the row PK (Kafka eventId header matches payload).
        assertThat(env.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(env.get("eventType").asText())
                .isEqualTo(AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT);
        assertThat(env.get("source").asText()).isEqualTo("finance-platform-account-service");
        assertThat(env.get("occurredAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(env.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(env.get("partitionKey").asText()).isEqualTo("acc-1");

        JsonNode payload = env.get("payload");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("transactionId").asText()).isEqualTo("txn-9");
        assertThat(payload.get("screeningRef").asText()).isEqualTo("screen-ref-7");
        assertThat(payload.get("queuedReviewId").asText()).isEqualTo("review-3");
    }
}
