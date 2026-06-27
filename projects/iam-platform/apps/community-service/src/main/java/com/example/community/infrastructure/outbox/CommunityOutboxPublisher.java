package com.example.community.infrastructure.outbox;

import com.example.community.infrastructure.persistence.CommunityOutboxJpaEntity;
import com.example.community.infrastructure.persistence.CommunityOutboxJpaRepository;
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
 * community-service outbox relay (TASK-BE-455 — outbox v1 → v2). A thin wrapper
 * around the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring the in-worktree
 * auth-service's {@code AuthOutboxPublisher} + finance account-service's
 * {@code AccountOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the community
 * specifics: the {@code community_outbox} table via
 * {@link CommunityOutboxJpaRepository}, the topic mapping, the {@code community}
 * metric prefix, and the schedule cadence. Replaces the v1
 * {@code CommunityOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>No gate.</b> The v1 {@code CommunityOutboxPollingScheduler} was an
 * unconditional {@code @Component} with no {@code @ConditionalOnProperty} — the
 * relay was always active ({@code @EnableScheduling} on
 * {@code CommunityApplication}). That is preserved: no gate is added.
 *
 * <p><b>Plain metrics.</b> The v1 scheduler kept no custom failure counter, so the
 * relay uses a plain {@link MicrometerOutboxMetrics} — there is no
 * {@code community_outbox_publish_failures_total} to preserve. The standard
 * {@code community.outbox.pending.count} gauge is added.
 *
 * <p>Topic resolution: ported VERBATIM from the v1
 * {@code CommunityOutboxPollingScheduler.resolveTopic} — iam topics are bare (no
 * {@code .v1} suffix); each {@code community.*} event maps to its identically-named
 * topic. An unmapped eventType is rejected with {@link IllegalArgumentException}.
 */
@Component
public class CommunityOutboxPublisher extends AbstractOutboxPublisher<CommunityOutboxJpaEntity> {

    static final String TOPIC_POST_PUBLISHED = "community.post.published";
    static final String TOPIC_COMMENT_CREATED = "community.comment.created";
    static final String TOPIC_REACTION_ADDED = "community.reaction.added";

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
                new MicrometerOutboxMetrics(meterRegistry, "community"),
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
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM
     * from the v1 {@code CommunityOutboxPollingScheduler.resolveTopic} (iam = bare
     * topic names, no {@code .v1} suffix), incl. reject-unmapped. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown community event type: null");
        }
        return switch (eventType) {
            case "community.post.published" -> TOPIC_POST_PUBLISHED;
            case "community.comment.created" -> TOPIC_COMMENT_CREATED;
            case "community.reaction.added" -> TOPIC_REACTION_ADDED;
            default -> throw new IllegalArgumentException("Unknown community event type: " + eventType);
        };
    }
}
