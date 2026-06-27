package com.example.payment.adapter.out.event;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox writer for payment domain events (ADR-006 Scenario A;
 * TASK-BE-449 outbox v2).
 *
 * <p>Persists one {@link PaymentOutboxEntity} ({@code payment_outbox} table) per
 * event inside the caller's {@code @Transactional} boundary so the payment state
 * mutation and event publication commit atomically. The Kafka publish is deferred
 * to {@link PaymentOutboxPublisher}, which polls the outbox and retries on broker
 * failure.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter}. Wire is preserved exactly: the row
 * {@code payload} is the byte-identical serialized envelope (event_id / event_type /
 * occurred_at / source / tenant_id / payload), the routing-key {@code eventType}
 * constants and {@code aggregate_type}/{@code aggregate_id} (Kafka key = {@code paymentId})
 * are unchanged. The row {@code event_id} reuses the event's own envelope
 * {@code event_id} so the Kafka header {@code eventId} matches the payload.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class PaymentEventOutboxWriter implements PaymentEventPublisher {

    static final String AGGREGATE_TYPE = "Payment";
    static final String EVENT_TYPE_COMPLETED = "PaymentCompleted";
    static final String EVENT_TYPE_REFUNDED = "PaymentRefunded";
    static final String EVENT_TYPE_REFUND_STRANDED = "PaymentRefundStranded";
    static final String EVENT_TYPE_REFUND_UNRESOLVED = "PaymentRefundUnresolved";

    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        save(event.eventId(), EVENT_TYPE_COMPLETED, event.payload().paymentId(), event.occurredAt(), event);
    }

    @Override
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        save(event.eventId(), EVENT_TYPE_REFUNDED, event.payload().paymentId(), event.occurredAt(), event);
    }

    @Override
    public void publishPaymentRefundStranded(PaymentRefundStrandedEvent event) {
        save(event.eventId(), EVENT_TYPE_REFUND_STRANDED, event.payload().paymentId(), event.occurredAt(), event);
    }

    @Override
    public void publishPaymentRefundUnresolved(PaymentRefundUnresolvedEvent event) {
        save(event.eventId(), EVENT_TYPE_REFUND_UNRESOLVED, event.payload().paymentId(), event.occurredAt(), event);
    }

    private void save(String eventId, String eventType, String paymentId, String occurredAt, Object event) {
        outboxRepository.save(new PaymentOutboxEntity(
                UUID.fromString(eventId),
                eventType,
                AGGREGATE_TYPE,
                paymentId,
                null, // partition_key: publisher falls back to aggregateId (paymentId)
                serialize(event),
                Instant.parse(occurredAt)));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event", e);
        }
    }
}
