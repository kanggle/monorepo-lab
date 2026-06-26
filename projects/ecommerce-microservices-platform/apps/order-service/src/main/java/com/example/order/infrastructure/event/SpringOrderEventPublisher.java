package com.example.order.infrastructure.event;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderConfirmedEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.infrastructure.persistence.OrderOutboxEntity;
import com.example.order.infrastructure.persistence.OrderOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * order-service outbox write path (TASK-BE-448, outbox v2).
 *
 * <p>Persists one {@link OrderOutboxEntity} ({@code order_outbox} table) per domain
 * event in the caller's {@code @Transactional} (the saga step), so the state change
 * and the outbox row commit atomically. The {@link OrderOutboxPublisher} relay
 * drains the table to Kafka.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter}. Wire is preserved exactly: the row
 * {@code payload} is the byte-identical serialized envelope, the routing-key
 * {@code eventType} (literal, as v1) and the {@code aggregate_type}/{@code aggregate_id}
 * (Kafka key = {@code orderId}) are unchanged. The row {@code event_id} reuses the
 * event's own envelope {@code event_id} so the Kafka header {@code eventId} matches
 * the payload.
 *
 * <p>{@code @Profile("!standalone")} so the standalone (H2, no Kafka) profile uses
 * {@code StandaloneOrderEventPublisher} (synchronous REST) instead.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class SpringOrderEventPublisher implements OrderEventPublisher {

    private static final String AGGREGATE_TYPE = "Order";

    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishOrderPlaced(OrderPlacedEvent event) {
        save(event.eventId(), "OrderPlaced", event.payload().orderId(), event.occurredAt(), event);
    }

    @Override
    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        save(event.eventId(), "OrderConfirmed", event.payload().orderId(), event.occurredAt(), event);
    }

    @Override
    public void publishOrderCancelled(OrderCancelledEvent event) {
        save(event.eventId(), "OrderCancelled", event.payload().orderId(), event.occurredAt(), event);
    }

    @Override
    public void publishOrderSagaRecoveryExhausted(OrderSagaRecoveryExhaustedEvent event) {
        save(event.eventId(), "OrderSagaRecoveryExhausted", event.payload().orderId(),
                event.occurredAt(), event);
    }

    private void save(String eventId, String eventType, String orderId, String occurredAt, Object event) {
        outboxRepository.save(new OrderOutboxEntity(
                UUID.fromString(eventId),
                eventType,
                AGGREGATE_TYPE,
                orderId,
                null, // partition_key: publisher falls back to aggregateId (orderId)
                serialize(event),
                Instant.parse(occurredAt)));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
