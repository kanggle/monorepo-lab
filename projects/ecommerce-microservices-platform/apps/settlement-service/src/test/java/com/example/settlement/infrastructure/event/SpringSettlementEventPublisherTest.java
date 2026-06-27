package com.example.settlement.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.infrastructure.persistence.SettlementOutboxEntity;
import com.example.settlement.infrastructure.persistence.SettlementOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link SpringSettlementEventPublisher} write path (TASK-BE-447,
 * outbox v2). Asserts the event persists a {@code settlement_outbox} row whose
 * wire-relevant fields are preserved exactly: the row {@code event_id} reuses the
 * envelope {@code event_id}, the payload is the byte-identical serialized envelope,
 * {@code aggregate_id} is the {@code periodId} (Kafka key source), and the routing
 * key {@code eventType} is {@code settlement.period.closed.v1}.
 */
class SpringSettlementEventPublisherTest {

    private final SettlementOutboxRepository repository = mock(SettlementOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpringSettlementEventPublisher publisher =
            new SpringSettlementEventPublisher(repository, objectMapper);

    @Test
    void publishPeriodClosed_persistsV2Row_preservingEnvelopeIdPayloadAndKey() throws Exception {
        String eventId = UUID.randomUUID().toString();
        SettlementPeriodClosedEvent.Payload payload = new SettlementPeriodClosedEvent.Payload(
                "period-1", "ecommerce", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z",
                "2026-07-01T00:00:05Z", 1,
                List.of(new SettlementPeriodClosedEvent.PayoutLine("seller-1", 9000L, 1000L, 3)));
        SettlementPeriodClosedEvent event = SettlementPeriodClosedEvent.of(
                eventId, "ecommerce", Instant.parse("2026-07-01T00:00:05Z"), payload);

        publisher.publishPeriodClosed(event);

        ArgumentCaptor<SettlementOutboxEntity> captor = ArgumentCaptor.forClass(SettlementOutboxEntity.class);
        verify(repository).save(captor.capture());
        SettlementOutboxEntity row = captor.getValue();

        assertThat(row.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(row.getEventType()).isEqualTo(SettlementPeriodClosedEvent.EVENT_TYPE);
        assertThat(row.getAggregateType()).isEqualTo("SettlementPeriod");
        assertThat(row.getAggregateId()).isEqualTo("period-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-07-01T00:00:05Z"));
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(row.getPublishedAt()).isNull();
    }
}
