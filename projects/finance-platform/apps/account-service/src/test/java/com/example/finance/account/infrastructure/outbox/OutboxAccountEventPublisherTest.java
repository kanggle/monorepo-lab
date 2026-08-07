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
 * <p>Pins the persisted {@code account_outbox} row's envelope shape, so
 * {@code ledger-service} and other {@code finance.*} consumers (dedupe on
 * {@code eventId}) cannot be broken silently (AC-2 / F2).
 * {@code publishSanctionHit} (strings only) exercises the envelope writer
 * deterministically; the per-event payload builders + the full persist→Kafka
 * round-trip are covered by {@code AccountOutboxRelayIntegrationTest}.
 *
 * <p>🔴 <strong>This assertion used to protect a defect (TASK-FIN-BE-068).</strong>
 * It pinned the envelope as EXACTLY seven fields — {@code eventId, eventType,
 * source, occurredAt, schemaVersion, partitionKey, payload} — and that list was
 * missing {@code tenantId}, which both event contracts
 * ({@code finance-account-events.md} § Envelope, {@code finance-ledger-events.md})
 * require. So the guard did its job perfectly and guarded the wrong thing: written
 * to prove "the wire did not change across the refactor", it was never diffed
 * against the contract the wire is supposed to implement, and a
 * {@code containsExactly} turned a missing required field into a pinned invariant.
 * Downstream, ledger-service defaulted the absent tenant to the literal
 * {@code "finance"} and filed every journal entry under it.
 *
 * <p>The lesson is in the <em>direction</em> of the comparison. "Unchanged since the
 * last refactor" is a weaker predicate than "conforms to the contract", and only the
 * second one can catch a field that has been absent from the start — an omission is
 * invisible to any check whose baseline is the current output.
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

        publisher.publishSanctionHit("demo-corp", "acc-1", "txn-9", "screen-ref-7", "review-3");

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

        // The envelope, as the event contracts define it (AC-2 — consumers unbroken).
        JsonNode env = objectMapper.readTree(row.getPayload());
        assertThat(env.fieldNames()).toIterable().containsExactly(
                "eventId", "eventType", "source", "occurredAt", "tenantId",
                "schemaVersion", "partitionKey", "payload");
        // eventId in the envelope == the row PK (Kafka eventId header matches payload).
        assertThat(env.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(env.get("eventType").asText())
                .isEqualTo(AccountEventPublisher.EVENT_COMPLIANCE_SANCTION_HIT);
        assertThat(env.get("source").asText()).isEqualTo("finance-platform-account-service");
        assertThat(env.get("occurredAt").asText()).isEqualTo(clock.instant().toString());
        // The caller's tenant, verbatim — NOT the literal "finance". A ledger entry
        // filed under the wrong tenant is unreadable by the operator who owns the money.
        assertThat(env.get("tenantId").asText()).isEqualTo("demo-corp");
        assertThat(env.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(env.get("partitionKey").asText()).isEqualTo("acc-1");

        JsonNode payload = env.get("payload");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("transactionId").asText()).isEqualTo("txn-9");
        assertThat(payload.get("screeningRef").asText()).isEqualTo("screen-ref-7");
        assertThat(payload.get("queuedReviewId").asText()).isEqualTo("review-3");
    }
}
