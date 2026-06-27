package com.example.fanplatform.membership.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaEntity;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link OutboxMembershipEventPublisher} write path
 * (TASK-FAN-BE-020, outbox v2).
 *
 * <p>Asserts each domain event persists a {@code membership_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1 lib
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope
 * ({@code eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey,
 * payload}) in that field order, the row {@code event_id} reused as the envelope
 * {@code eventId}, {@code aggregate_type}/{@code aggregate_id}/{@code event_type}
 * matching the v1 call, and {@code partition_key} left null so the relay falls
 * back to {@code aggregateId} (the v1 Kafka key). Conditional payload omissions
 * (the expired event carries no plan/cancel fields) are pinned too.
 */
class OutboxMembershipEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);
    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

    private final MembershipOutboxJpaRepository repository = mock(MembershipOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxMembershipEventPublisher publisher =
            new OutboxMembershipEventPublisher(repository, objectMapper, CLOCK);

    @Test
    void publishActivated_persistsV2Row_withCanonicalEnvelopeAndPreservedKeyFields() throws Exception {
        publisher.publishActivated("m1", "fan-platform", "acc1", MembershipTier.PREMIUM, 1,
                NOW, NOW.plusSeconds(100), NOW);

        MembershipOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(MembershipEventPublisher.EVENT_ACTIVATED);
        assertThat(row.getAggregateType()).isEqualTo("membership");
        assertThat(row.getAggregateId()).isEqualTo("m1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());
        assertThat(row.getPublishedAt()).isNull();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(MembershipEventPublisher.EVENT_ACTIVATED);
        assertThat(envelope.get("source").asText()).isEqualTo("fan-platform-membership-service");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("m1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("membershipId").asText()).isEqualTo("m1");
        assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc1");
        assertThat(payload.get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(payload.get("planMonths").asInt()).isEqualTo(1);
        assertThat(payload.get("validFrom").asText()).isEqualTo(NOW.toString());
        assertThat(payload.get("validTo").asText()).isEqualTo(NOW.plusSeconds(100).toString());
    }

    @Test
    void publishCanceled_persistsV2Row_withReasonPayload() throws Exception {
        publisher.publishCanceled("m1", "fan-platform", "acc1", MembershipTier.PREMIUM,
                "done", NOW, NOW);

        MembershipOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(MembershipEventPublisher.EVENT_CANCELED);
        assertThat(row.getAggregateId()).isEqualTo("m1");

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(MembershipEventPublisher.EVENT_CANCELED);
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("reason").asText()).isEqualTo("done");
        assertThat(payload.get("canceledAt").asText()).isEqualTo(NOW.toString());
    }

    @Test
    void publishExpired_persistsV2Row_withValidToAndConditionalOmissions() throws Exception {
        Instant validTo = NOW.plusSeconds(100);
        publisher.publishExpired("m1", "fan-platform", "acc1", MembershipTier.MEMBERS_ONLY, validTo, NOW);

        MembershipOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(MembershipEventPublisher.EVENT_EXPIRED);

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("m1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("tier").asText()).isEqualTo("MEMBERS_ONLY");
        assertThat(payload.get("validTo").asText()).isEqualTo(validTo.toString());
        // expired carries no plan / cancel fields (v1 shape preserved)
        assertThat(payload.has("planMonths")).isFalse();
        assertThat(payload.has("canceledAt")).isFalse();
    }

    private MembershipOutboxJpaEntity capturedRow() {
        ArgumentCaptor<MembershipOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(MembershipOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
