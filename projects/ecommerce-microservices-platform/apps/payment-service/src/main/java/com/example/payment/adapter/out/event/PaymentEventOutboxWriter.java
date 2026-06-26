package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.OutboxWriter;
import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox writer for payment domain events (ADR-006 Scenario A).
 *
 * <p>Persists the serialized event envelope to the {@code outbox} table inside
 * the caller's {@code @Transactional} boundary so the payment state mutation
 * and event publication commit atomically. The Kafka publish itself is
 * deferred to {@link PaymentEventOutboxRelay}, which polls the outbox and
 * retries on broker failure — closing the silent-loss gap that the prior
 * direct {@code KafkaTemplate.send} path left open.
 *
 * <p>Envelope shape (event_id / event_type / occurred_at / source / payload)
 * is preserved from the existing {@link PaymentCompletedEvent} /
 * {@link PaymentRefundedEvent} records — no change to the published contract.
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

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        outboxWriter.save(
                AGGREGATE_TYPE,
                event.payload().paymentId(),
                EVENT_TYPE_COMPLETED,
                serialize(event)
        );
    }

    @Override
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        outboxWriter.save(
                AGGREGATE_TYPE,
                event.payload().paymentId(),
                EVENT_TYPE_REFUNDED,
                serialize(event)
        );
    }

    @Override
    public void publishPaymentRefundStranded(PaymentRefundStrandedEvent event) {
        outboxWriter.save(
                AGGREGATE_TYPE,
                event.payload().paymentId(),
                EVENT_TYPE_REFUND_STRANDED,
                serialize(event)
        );
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event", e);
        }
    }
}
