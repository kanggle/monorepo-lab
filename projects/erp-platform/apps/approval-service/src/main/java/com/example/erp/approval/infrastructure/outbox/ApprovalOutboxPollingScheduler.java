package com.example.erp.approval.infrastructure.outbox;

import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * approval-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic mapping.
 * {@code .v1} suffix follows the platform event versioning convention
 * (erp-approval-events.md § Topics).
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_APPROVAL_SUBMITTED = "erp.approval.submitted.v1";
    static final String TOPIC_APPROVAL_APPROVED = "erp.approval.approved.v1";
    static final String TOPIC_APPROVAL_REJECTED = "erp.approval.rejected.v1";
    static final String TOPIC_APPROVAL_WITHDRAWN = "erp.approval.withdrawn.v1";

    private final Counter publishFailures;

    public ApprovalOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                          KafkaTemplate<String, String> kafkaTemplate,
                                          MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("approval_outbox_publish_failures_total")
                .description("Outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case ApprovalEventPublisher.EVENT_APPROVAL_SUBMITTED -> TOPIC_APPROVAL_SUBMITTED;
            case ApprovalEventPublisher.EVENT_APPROVAL_APPROVED -> TOPIC_APPROVAL_APPROVED;
            case ApprovalEventPublisher.EVENT_APPROVAL_REJECTED -> TOPIC_APPROVAL_REJECTED;
            case ApprovalEventPublisher.EVENT_APPROVAL_WITHDRAWN -> TOPIC_APPROVAL_WITHDRAWN;
            default -> throw new IllegalArgumentException(
                    "Unknown approval event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
