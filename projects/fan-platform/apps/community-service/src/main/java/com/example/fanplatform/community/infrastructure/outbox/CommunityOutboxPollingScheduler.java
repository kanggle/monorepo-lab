package com.example.fanplatform.community.infrastructure.outbox;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * community-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic mapping
 * (one of the four {@code community.*} topics defined in
 * {@link CommunityEventPublisher}).
 *
 * <p>The {@code .v1} suffix follows the platform event versioning convention
 * (see {@code platform/event-driven-policy.md} and {@code TASK-FAN-BE-002 §
 * Acceptance Criteria}).
 */
@Slf4j
@Component
public class CommunityOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_POST_PUBLISHED = "community.post.published.v1";
    static final String TOPIC_POST_STATUS_CHANGED = "community.post.status_changed.v1";
    static final String TOPIC_COMMENT_ADDED = "community.comment.added.v1";
    static final String TOPIC_REACTION_ADDED = "community.reaction.added.v1";

    private final Counter publishFailures;

    public CommunityOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("community_outbox_publish_failures_total")
                .description("Number of outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case CommunityEventPublisher.EVENT_POST_PUBLISHED -> TOPIC_POST_PUBLISHED;
            case CommunityEventPublisher.EVENT_POST_STATUS_CHANGED -> TOPIC_POST_STATUS_CHANGED;
            case CommunityEventPublisher.EVENT_COMMENT_ADDED -> TOPIC_COMMENT_ADDED;
            case CommunityEventPublisher.EVENT_REACTION_ADDED -> TOPIC_REACTION_ADDED;
            default -> throw new IllegalArgumentException("Unknown community event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
