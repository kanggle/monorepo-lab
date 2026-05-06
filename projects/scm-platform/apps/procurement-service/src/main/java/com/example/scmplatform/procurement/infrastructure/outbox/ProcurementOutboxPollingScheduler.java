package com.example.scmplatform.procurement.infrastructure.outbox;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * procurement-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic
 * mapping. {@code .v1} suffix follows the platform event versioning
 * convention.
 *
 * <p>Disabled when {@code outbox.polling.enabled=false} — slice tests and
 * the {@code test} profile use this to avoid background polling during
 * unit / integration runs that don't exercise Kafka.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class ProcurementOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_PO_SUBMITTED = "scm.procurement.po.submitted.v1";
    static final String TOPIC_PO_ACKNOWLEDGED = "scm.procurement.po.acknowledged.v1";
    static final String TOPIC_PO_CONFIRMED = "scm.procurement.po.confirmed.v1";
    static final String TOPIC_PO_CANCELED = "scm.procurement.po.canceled.v1";
    static final String TOPIC_PO_RECEIVED = "scm.procurement.po.received.v1";
    static final String TOPIC_PO_CLOSED = "scm.procurement.po.closed.v1";
    static final String TOPIC_ASN_RECEIVED = "scm.procurement.asn.received.v1";

    private final Counter publishFailures;

    public ProcurementOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                             KafkaTemplate<String, String> kafkaTemplate,
                                             MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("procurement_outbox_publish_failures_total")
                .description("Number of outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case ProcurementEventPublisher.EVENT_PO_SUBMITTED -> TOPIC_PO_SUBMITTED;
            case ProcurementEventPublisher.EVENT_PO_ACKNOWLEDGED -> TOPIC_PO_ACKNOWLEDGED;
            case ProcurementEventPublisher.EVENT_PO_CONFIRMED -> TOPIC_PO_CONFIRMED;
            case ProcurementEventPublisher.EVENT_PO_CANCELED -> TOPIC_PO_CANCELED;
            case ProcurementEventPublisher.EVENT_PO_RECEIVED -> TOPIC_PO_RECEIVED;
            case ProcurementEventPublisher.EVENT_PO_CLOSED -> TOPIC_PO_CLOSED;
            case ProcurementEventPublisher.EVENT_ASN_RECEIVED -> TOPIC_ASN_RECEIVED;
            default -> throw new IllegalArgumentException(
                    "Unknown procurement event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
