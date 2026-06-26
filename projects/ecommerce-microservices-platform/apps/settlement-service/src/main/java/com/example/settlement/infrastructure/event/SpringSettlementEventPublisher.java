package com.example.settlement.infrastructure.event;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.infrastructure.persistence.SettlementOutboxEntity;
import com.example.settlement.infrastructure.persistence.SettlementOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox-backed implementation of {@link SettlementEventPublisher} (TASK-BE-447,
 * outbox v2). Persists one {@link SettlementOutboxEntity} ({@code settlement_outbox}
 * table) per event in the caller's {@code @Transactional} (the close use-case) — the
 * {@link SettlementOutboxPublisher} relay drains it to the
 * {@code settlement.period.closed} topic.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter}. Wire is preserved exactly: the row
 * {@code payload} is the byte-identical serialized snake_case envelope, the routing
 * key {@code eventType} ({@code settlement.period.closed.v1}) and the
 * {@code aggregate_type}/{@code aggregate_id} (Kafka key = {@code periodId}) are
 * unchanged. The row {@code event_id} reuses the event's own envelope {@code event_id}
 * so the Kafka header {@code eventId} matches the payload.
 *
 * <p>{@code @Profile("!standalone")} so the standalone (H2) profile — which has no
 * Kafka/dispatcher — uses {@code NoopSettlementEventPublisher} (no outbox row written).
 */
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class SpringSettlementEventPublisher implements SettlementEventPublisher {

    private static final String AGGREGATE_TYPE = "SettlementPeriod";

    private final SettlementOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPeriodClosed(SettlementPeriodClosedEvent event) {
        outboxRepository.save(new SettlementOutboxEntity(
                UUID.fromString(event.eventId()),
                event.eventType(),
                AGGREGATE_TYPE,
                event.payload().periodId(),
                null, // partition_key: publisher falls back to aggregateId (periodId)
                serialize(event),
                Instant.parse(event.occurredAt())));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize settlement event", e);
        }
    }
}
