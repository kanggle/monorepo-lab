package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.application.HandleMembershipEventUseCase;
import com.example.fanplatform.notification.infrastructure.messaging.ConsumerMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The ONLY inbound Kafka surface. Subscribes to the two <b>emitted</b> membership
 * lifecycle topics (architecture.md § Subscribed Topics); it does NOT subscribe
 * to {@code fan.membership.expired.v1} (forward-declared but not emitted upstream
 * — a dead consumer). Both listeners share one consumer group
 * ({@code notification-service-membership-events}); the producer's
 * {@code membershipId} partition key preserves per-membership ordering.
 *
 * <p>Each listener parses the envelope and delegates to
 * {@link HandleMembershipEventUseCase}; it embeds NO business logic. A per-message
 * exception is rethrown so the container's {@code DefaultErrorHandler} retries
 * (transient) or routes straight to {@code <topic>.dlq} (non-retryable:
 * unsupported schema / malformed) — emit-not-throw discipline (feedback §18): the
 * exception escapes to the error handler, which prevents the partition stall, it
 * is never swallowed.
 */
@Slf4j
@Component
public class MembershipEventConsumer {

    static final String TOPIC_ACTIVATED = "fan.membership.activated.v1";
    static final String TOPIC_CANCELED = "fan.membership.canceled.v1";
    static final String GROUP = "notification-service-membership-events";

    private final MembershipEventParser parser;
    private final HandleMembershipEventUseCase useCase;
    private final ConsumerMetrics metrics;

    public MembershipEventConsumer(MembershipEventParser parser,
                                   HandleMembershipEventUseCase useCase,
                                   ConsumerMetrics metrics) {
        this.parser = parser;
        this.useCase = useCase;
        this.metrics = metrics;
    }

    @KafkaListener(topics = TOPIC_ACTIVATED, groupId = GROUP)
    public void onActivated(ConsumerRecord<String, String> record) {
        handle(record);
    }

    @KafkaListener(topics = TOPIC_CANCELED, groupId = GROUP)
    public void onCanceled(ConsumerRecord<String, String> record) {
        handle(record);
    }

    private void handle(ConsumerRecord<String, String> record) {
        try {
            MembershipEvent event = parser.parse(record.value());
            useCase.handle(event);
            metrics.processed(record.topic());
        } catch (RuntimeException e) {
            // Count + rethrow so the DefaultErrorHandler routes to retry/DLQ.
            // The exception is never swallowed (emit-not-throw): letting it
            // escape to the configured error handler is the DLQ mechanism, and
            // that handler prevents the partition from stalling.
            metrics.failed(record.topic());
            log.warn("Handler failed for topic={} key={} offset={}: {}",
                    record.topic(), record.key(), record.offset(), e.toString());
            throw e;
        }
    }
}
