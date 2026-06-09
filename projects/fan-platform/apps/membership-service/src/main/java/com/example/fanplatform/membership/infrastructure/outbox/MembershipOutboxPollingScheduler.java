package com.example.fanplatform.membership.infrastructure.outbox;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * membership-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic mapping.
 *
 * <p>The {@code .v1} suffix follows the platform event versioning convention
 * (see {@code platform/event-driven-policy.md} and
 * {@code specs/contracts/events/fan-membership-events.md}). On Kafka send failure
 * the {@code membership_outbox_publish_failures_total} metric increments and the
 * row is retried on the next tick.
 */
@Slf4j
@Component
public class MembershipOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_ACTIVATED = "fan.membership.activated.v1";
    static final String TOPIC_CANCELED = "fan.membership.canceled.v1";

    private final Counter publishFailures;

    public MembershipOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                            KafkaTemplate<String, String> kafkaTemplate,
                                            MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("membership_outbox_publish_failures_total")
                .description("Number of outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case MembershipEventPublisher.EVENT_ACTIVATED -> TOPIC_ACTIVATED;
            case MembershipEventPublisher.EVENT_CANCELED -> TOPIC_CANCELED;
            default -> throw new IllegalArgumentException("Unknown membership event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
