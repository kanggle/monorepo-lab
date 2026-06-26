package com.example.review.infrastructure.event;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * review-service outbox publisher (TASK-BE-445, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff and the {@code eventId}/{@code eventType}
 * record headers all live in {@code libs/java-messaging}. This class supplies the
 * review-service specifics: the {@code review_outbox} repository, a
 * {@link TopicResolver} mapping {@code ReviewCreated/Updated/Deleted →
 * review.review.{created,updated,deleted}} (ported verbatim from the v1
 * {@code ReviewOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped), a
 * {@link MicrometerOutboxMetrics} with the {@code review} prefix + a preserved
 * {@code review.outbox.pending.count} gauge, and the {@code @Scheduled} trigger.
 *
 * <p>No {@code @Profile} gate — the v1 {@code ReviewOutboxPollingScheduler} ran
 * unconditionally; this preserves that.
 */
@Component
public class ReviewOutboxPublisher extends AbstractOutboxPublisher<ReviewOutboxEntity> {

    static final String TOPIC_REVIEW_CREATED = "review.review.created";
    static final String TOPIC_REVIEW_UPDATED = "review.review.updated";
    static final String TOPIC_REVIEW_DELETED = "review.review.deleted";

    public ReviewOutboxPublisher(ReviewOutboxRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 TransactionTemplate transactionTemplate,
                                 Clock clock,
                                 MeterRegistry meterRegistry,
                                 @Value("${review.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "review"),
                clock,
                batchSize);

        Gauge.builder("review.outbox.pending.count", repository,
                        ReviewOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished review outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${review.outbox.poll-ms:1000}",
            initialDelayString = "${review.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return ReviewOutboxPublisher::topicFor;
    }

    /**
     * Resolves the domain event type to its Kafka topic. Ported verbatim from
     * the v1 {@code ReviewOutboxPollingScheduler.resolveTopic} — unknown event
     * types are rejected with {@link IllegalArgumentException}.
     *
     * <p>Exposed package-private + static so the unit test can exercise the
     * mapping + reject-unmapped behaviour directly.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown review event type: null");
        }
        return switch (eventType) {
            case "ReviewCreated" -> TOPIC_REVIEW_CREATED;
            case "ReviewUpdated" -> TOPIC_REVIEW_UPDATED;
            case "ReviewDeleted" -> TOPIC_REVIEW_DELETED;
            default -> throw new IllegalArgumentException("Unknown review event type: " + eventType);
        };
    }
}
