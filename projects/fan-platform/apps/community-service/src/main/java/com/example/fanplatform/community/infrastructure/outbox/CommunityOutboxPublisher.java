package com.example.fanplatform.community.infrastructure.outbox;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaEntity;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaRepository;
import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.OutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * community-service outbox publisher (TASK-FAN-BE-021, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff and the {@code eventId}/{@code eventType}
 * record headers all live in {@code libs/java-messaging}. Replaces the v1
 * {@code CommunityOutboxPollingScheduler extends OutboxPollingScheduler}.
 * Supplies the community-service specifics:
 * <ul>
 *   <li>the {@code community_outbox} repository via
 *       {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping the four {@code community.*} event types
 *       to their existing {@code ….v1} topics (ported verbatim from the v1
 *       {@code CommunityOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code community} prefix,
 *       <b>wrapped</b> so a publish failure ALSO increments the pre-existing
 *       {@code community_outbox_publish_failures_total} counter — preserving the
 *       v1 {@code onKafkaSendFailure} hook (dashboard/alert continuity)</li>
 *   <li>a {@code community.outbox.pending.count} gauge (new in v2)</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>The v1 relay was an unconditional {@code @Component} (no
 * {@code @ConditionalOnProperty}); this preserves that — the relay bean always
 * exists. The poll/initial-delay knobs are read from {@code community.outbox.*}.
 */
@Component
public class CommunityOutboxPublisher extends AbstractOutboxPublisher<CommunityOutboxJpaEntity> {

    static final String TOPIC_POST_PUBLISHED = "community.post.published.v1";
    static final String TOPIC_POST_STATUS_CHANGED = "community.post.status_changed.v1";
    static final String TOPIC_COMMENT_ADDED = "community.comment.added.v1";
    static final String TOPIC_REACTION_ADDED = "community.reaction.added.v1";

    public CommunityOutboxPublisher(CommunityOutboxJpaRepository repository,
                                    KafkaTemplate<String, String> kafkaTemplate,
                                    TransactionTemplate transactionTemplate,
                                    Clock clock,
                                    MeterRegistry meterRegistry,
                                    @Value("${community.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                failurePreservingMetrics(meterRegistry),
                clock,
                batchSize);

        Gauge.builder("community.outbox.pending.count", repository,
                        CommunityOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished community outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${community.outbox.poll-ms:1000}",
            initialDelayString = "${community.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return CommunityOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code CommunityOutboxPollingScheduler.resolveTopic}, incl.
     * reject-unmapped (treated as a non-retryable poison-pill row by the publisher
     * loop). Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown community event type: null");
        }
        return switch (eventType) {
            case CommunityEventPublisher.EVENT_POST_PUBLISHED -> TOPIC_POST_PUBLISHED;
            case CommunityEventPublisher.EVENT_POST_STATUS_CHANGED -> TOPIC_POST_STATUS_CHANGED;
            case CommunityEventPublisher.EVENT_COMMENT_ADDED -> TOPIC_COMMENT_ADDED;
            case CommunityEventPublisher.EVENT_REACTION_ADDED -> TOPIC_REACTION_ADDED;
            default -> throw new IllegalArgumentException("Unknown community event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code community_outbox_publish_failures_total} counter
     * (preserving the v1 {@code CommunityOutboxPollingScheduler.onKafkaSendFailure}
     * hook). The wrapper guards on {@code eventType != null} (poll-level failures,
     * which the lib reports with a null eventType, are not counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "community");
        Counter publishFailures = Counter.builder("community_outbox_publish_failures_total")
                .description("Number of outbox events that failed to publish to Kafka.")
                .register(registry);
        return new OutboxMetrics() {
            @Override
            public void recordPublishSuccess(String eventType, Duration lag) {
                base.recordPublishSuccess(eventType, lag);
            }

            @Override
            public void recordPublishFailure(String eventType, String reason) {
                base.recordPublishFailure(eventType, reason);
                if (eventType != null) {
                    publishFailures.increment();
                }
            }
        };
    }
}
