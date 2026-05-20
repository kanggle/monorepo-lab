package com.example.erp.masterdata.infrastructure.outbox;

import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * masterdata-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic
 * mapping. {@code .v1} suffix follows the platform event versioning
 * convention (erp-masterdata-events.md § Topics).
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class MasterdataOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_DEPARTMENT_CHANGED = "erp.masterdata.department.changed.v1";
    static final String TOPIC_EMPLOYEE_CHANGED = "erp.masterdata.employee.changed.v1";
    static final String TOPIC_JOBGRADE_CHANGED = "erp.masterdata.jobgrade.changed.v1";
    static final String TOPIC_COSTCENTER_CHANGED = "erp.masterdata.costcenter.changed.v1";
    static final String TOPIC_BUSINESSPARTNER_CHANGED = "erp.masterdata.businesspartner.changed.v1";

    private final Counter publishFailures;

    public MasterdataOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                            KafkaTemplate<String, String> kafkaTemplate,
                                            MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("masterdata_outbox_publish_failures_total")
                .description("Outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case MasterdataEventPublisher.EVENT_DEPARTMENT_CHANGED -> TOPIC_DEPARTMENT_CHANGED;
            case MasterdataEventPublisher.EVENT_EMPLOYEE_CHANGED -> TOPIC_EMPLOYEE_CHANGED;
            case MasterdataEventPublisher.EVENT_JOBGRADE_CHANGED -> TOPIC_JOBGRADE_CHANGED;
            case MasterdataEventPublisher.EVENT_COSTCENTER_CHANGED -> TOPIC_COSTCENTER_CHANGED;
            case MasterdataEventPublisher.EVENT_BUSINESSPARTNER_CHANGED -> TOPIC_BUSINESSPARTNER_CHANGED;
            default -> throw new IllegalArgumentException(
                    "Unknown masterdata event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
