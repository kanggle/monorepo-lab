package com.example.fanplatform.notification.application.consumer;

import com.example.fanplatform.notification.application.HandleCommunityEventUseCase;
import com.example.fanplatform.notification.infrastructure.messaging.ConsumerMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The second inbound Kafka surface (TASK-FAN-BE-026), alongside
 * {@link MembershipEventConsumer}. Subscribes to the two <b>community
 * interaction</b> topics under its own consumer group
 * ({@code notification-service-community-events}, per the {@code <service>-<purpose>}
 * convention) so community lag/replay is independent of the membership
 * subscription. The producer's {@code postId} partition key preserves
 * per-post ordering.
 *
 * <p>Each listener parses the envelope and delegates to
 * {@link HandleCommunityEventUseCase}; it embeds NO business logic. A per-message
 * exception is rethrown so the shared {@code DefaultErrorHandler}
 * ({@code KafkaConsumerConfig}) retries (transient) or routes straight to
 * {@code <topic>.dlq} (non-retryable: unsupported schema / malformed) —
 * emit-not-throw discipline: the exception escapes to the error handler, which
 * prevents the partition stall, it is never swallowed.
 *
 * <p><b>No sync coupling</b>: routing is purely from the enriched event payload
 * (community-events.md § Recipient-routing fields). This service holds zero
 * outbound synchronous calls to community-service.
 */
@Slf4j
@Component
public class CommunityEventConsumer {

    static final String TOPIC_COMMENT_ADDED = "community.comment.added.v1";
    static final String TOPIC_REACTION_ADDED = "community.reaction.added.v1";
    static final String GROUP = "notification-service-community-events";

    private final CommunityEventParser parser;
    private final HandleCommunityEventUseCase useCase;
    private final ConsumerMetrics metrics;

    public CommunityEventConsumer(CommunityEventParser parser,
                                  HandleCommunityEventUseCase useCase,
                                  ConsumerMetrics metrics) {
        this.parser = parser;
        this.useCase = useCase;
        this.metrics = metrics;
    }

    @KafkaListener(topics = TOPIC_COMMENT_ADDED, groupId = GROUP)
    public void onCommentAdded(ConsumerRecord<String, String> record) {
        handle(record);
    }

    @KafkaListener(topics = TOPIC_REACTION_ADDED, groupId = GROUP)
    public void onReactionAdded(ConsumerRecord<String, String> record) {
        handle(record);
    }

    private void handle(ConsumerRecord<String, String> record) {
        try {
            CommunityEvent event = parser.parse(record.value());
            useCase.handle(event);
            metrics.processed(record.topic());
        } catch (RuntimeException e) {
            metrics.failed(record.topic());
            log.warn("Handler failed for topic={} key={} offset={}: {}",
                    record.topic(), record.key(), record.offset(), e.toString());
            throw e;
        }
    }
}
