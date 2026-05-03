package com.example.fanplatform.artist.adapter.out.messaging;

import com.example.fanplatform.artist.adapter.out.event.ArtistEventPublisherAdapter;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * artist-service outbox relay. Inherits the polling loop from
 * {@code libs:java-messaging} and only declares the event-type → topic
 * mapping. Mirrors community-service's {@code CommunityOutboxPollingScheduler}.
 *
 * <p>The {@code .v1} suffix follows the platform event versioning convention
 * (see {@code platform/event-driven-policy.md} and
 * {@code projects/fan-platform/specs/contracts/events/artist-events.md}).
 */
@Slf4j
@Component
public class ArtistOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_REGISTERED = "artist.registered.v1";
    static final String TOPIC_PUBLISHED = "artist.published.v1";
    static final String TOPIC_UPDATED = "artist.updated.v1";
    static final String TOPIC_ARCHIVED = "artist.archived.v1";
    static final String TOPIC_GROUP_CREATED = "artist.group_created.v1";
    static final String TOPIC_GROUP_MEMBER_CHANGED = "artist.group_member_changed.v1";

    private final Counter publishFailures;

    public ArtistOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        MeterRegistry meterRegistry) {
        super(outboxPublisher, kafkaTemplate);
        this.publishFailures = Counter.builder("artist_outbox_publish_failures_total")
                .description("Number of outbox events that failed to publish to Kafka.")
                .register(meterRegistry);
    }

    @Override
    protected String resolveTopic(String eventType) {
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

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        publishFailures.increment();
    }
}
