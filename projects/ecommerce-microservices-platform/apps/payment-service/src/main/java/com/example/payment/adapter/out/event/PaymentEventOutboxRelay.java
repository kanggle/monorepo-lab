package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * payment-service outbox relay (ADR-006 Scenario A impl).
 *
 * <p>Inherits the polling loop from {@code libs/java-messaging}'s
 * {@link OutboxPollingScheduler} and declares only the event-type → topic
 * mapping and the failure metric wiring.
 *
 * <p>Failure callback delegates to
 * {@link PaymentMetricRecorder#incrementEventPublishFailure(String)} so the
 * existing {@code event_publish_failure_total} metric label semantics
 * (service=payment-service, event_type=...) are preserved unchanged from
 * the pre-migration direct-publish path.
 *
 * <p>Disabled when {@code outbox.polling.enabled=false} — used by unit /
 * slice tests and the {@code standalone} profile to avoid background
 * polling during runs that don't exercise Kafka.
 */
@Slf4j
@Component
@Profile("!standalone")
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventOutboxRelay extends OutboxPollingScheduler {

    static final String TOPIC_PAYMENT_COMPLETED = "payment.payment.completed";
    static final String TOPIC_PAYMENT_REFUNDED = "payment.payment.refunded";
    static final String TOPIC_PAYMENT_REFUND_STRANDED = "payment.alert.refund.stranded";

    private final PaymentMetricRecorder paymentMetricRecorder;

    public PaymentEventOutboxRelay(OutboxPublisher outboxPublisher,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   PaymentMetricRecorder paymentMetricRecorder) {
        super(outboxPublisher, kafkaTemplate);
        this.paymentMetricRecorder = paymentMetricRecorder;
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case PaymentEventOutboxWriter.EVENT_TYPE_COMPLETED -> TOPIC_PAYMENT_COMPLETED;
            case PaymentEventOutboxWriter.EVENT_TYPE_REFUNDED -> TOPIC_PAYMENT_REFUNDED;
            case PaymentEventOutboxWriter.EVENT_TYPE_REFUND_STRANDED -> TOPIC_PAYMENT_REFUND_STRANDED;
            default -> throw new IllegalArgumentException("Unknown payment event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        paymentMetricRecorder.incrementEventPublishFailure(eventType);
    }
}
