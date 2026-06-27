package com.example.fanplatform.artist.adapter.out.messaging;

import com.example.fanplatform.artist.adapter.out.event.ArtistEventPublisherAdapter;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaEntity;
import com.example.fanplatform.artist.adapter.out.persistence.ArtistOutboxJpaRepository;
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
 * artist-service outbox publisher (TASK-FAN-BE-022, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff and the {@code eventId}/{@code eventType}
 * record headers all live in {@code libs/java-messaging}. Replaces the v1
 * {@code ArtistOutboxPollingScheduler extends OutboxPollingScheduler}. Supplies
 * the artist-service specifics:
 * <ul>
 *   <li>the {@code artist_outbox} repository via
 *       {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping the six {@code artist.*} event types to
 *       their existing {@code ….v1} topics (ported verbatim from the v1
 *       {@code ArtistOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code artist} prefix,
 *       <b>wrapped</b> so a publish failure ALSO increments the pre-existing
 *       {@code artist_outbox_publish_failures_total} counter — preserving the v1
 *       {@code onKafkaSendFailure} hook (dashboard/alert continuity)</li>
 *   <li>a {@code artist.outbox.pending.count} gauge (new in v2)</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>The v1 relay was an unconditional {@code @Component} (no
 * {@code @ConditionalOnProperty}); this preserves that — the relay bean always
 * exists. The poll/initial-delay knobs are read from {@code artist.outbox.*}.
 */
@Component
public class ArtistOutboxPublisher extends AbstractOutboxPublisher<ArtistOutboxJpaEntity> {

    static final String TOPIC_REGISTERED = "artist.registered.v1";
    static final String TOPIC_PUBLISHED = "artist.published.v1";
    static final String TOPIC_UPDATED = "artist.updated.v1";
    static final String TOPIC_ARCHIVED = "artist.archived.v1";
    static final String TOPIC_GROUP_CREATED = "artist.group_created.v1";
    static final String TOPIC_GROUP_MEMBER_CHANGED = "artist.group_member_changed.v1";

    public ArtistOutboxPublisher(ArtistOutboxJpaRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 TransactionTemplate transactionTemplate,
                                 Clock clock,
                                 MeterRegistry meterRegistry,
                                 @Value("${artist.outbox.batch-size:100}") int batchSize) {
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

        Gauge.builder("artist.outbox.pending.count", repository,
                        ArtistOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished artist outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${artist.outbox.poll-ms:1000}",
            initialDelayString = "${artist.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return ArtistOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code ArtistOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped
     * (treated as a non-retryable poison-pill row by the publisher loop). Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown artist event type: null");
        }
        return switch (eventType) {
            case ArtistEventPublisherAdapter.EVENT_ARTIST_REGISTERED -> TOPIC_REGISTERED;
            case ArtistEventPublisherAdapter.EVENT_ARTIST_PUBLISHED -> TOPIC_PUBLISHED;
            case ArtistEventPublisherAdapter.EVENT_ARTIST_UPDATED -> TOPIC_UPDATED;
            case ArtistEventPublisherAdapter.EVENT_ARTIST_ARCHIVED -> TOPIC_ARCHIVED;
            case ArtistEventPublisherAdapter.EVENT_ARTIST_GROUP_CREATED -> TOPIC_GROUP_CREATED;
            case ArtistEventPublisherAdapter.EVENT_ARTIST_GROUP_MEMBER_CHANGED -> TOPIC_GROUP_MEMBER_CHANGED;
            default -> throw new IllegalArgumentException("Unknown artist event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code artist_outbox_publish_failures_total} counter
     * (preserving the v1 {@code ArtistOutboxPollingScheduler.onKafkaSendFailure}
     * hook). The wrapper guards on {@code eventType != null} (poll-level failures,
     * which the lib reports with a null eventType, are not counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "artist");
        Counter publishFailures = Counter.builder("artist_outbox_publish_failures_total")
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
