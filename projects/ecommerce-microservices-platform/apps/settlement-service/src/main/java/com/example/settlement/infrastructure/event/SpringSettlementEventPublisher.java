package com.example.settlement.infrastructure.event;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox-backed implementation of {@link SettlementEventPublisher}. Serializes the
 * snake_case envelope and appends an {@code outbox} row in the caller's
 * {@code @Transactional} (the close use-case) — transactional outbox, mirroring
 * order/payment-service's {@code SpringOrderEventPublisher}. The
 * {@code SettlementOutboxPollingScheduler} relays the row to the
 * {@code settlement.period.closed} topic.
 *
 * <p>{@code @Profile("!standalone")} so the standalone (H2) profile — which has no
 * Kafka/dispatcher — does not require the outbox relay path.
 */
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class SpringSettlementEventPublisher implements SettlementEventPublisher {

    private static final String AGGREGATE_TYPE = "SettlementPeriod";

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPeriodClosed(SettlementPeriodClosedEvent event) {
        String payload = serialize(event);
        outboxWriter.save(AGGREGATE_TYPE, event.payload().periodId(),
                event.eventType(), payload);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize settlement event", e);
        }
    }
}
