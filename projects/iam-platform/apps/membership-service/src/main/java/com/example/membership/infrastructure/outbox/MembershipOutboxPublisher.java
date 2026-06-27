package com.example.membership.infrastructure.outbox;

import com.example.membership.infrastructure.persistence.MembershipOutboxJpaEntity;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaRepository;
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
 * membership-service outbox relay (TASK-BE-454 — outbox v1 → v2). A thin wrapper
 * around the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring the in-worktree
 * auth-service's {@code AuthOutboxPublisher} + finance account-service's
 * {@code AccountOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the membership
 * specifics: the {@code membership_outbox} table via
 * {@link MembershipOutboxJpaRepository}, the topic mapping, the {@code membership}
 * metric prefix, and the schedule cadence. Replaces the v1
 * {@code MembershipOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>No gate.</b> The v1 {@code MembershipOutboxPollingScheduler} was an
 * unconditional {@code @Component} with no {@code @ConditionalOnProperty} — the
 * relay was always active ({@code @EnableScheduling} on
 * {@code MembershipApplication}). That is preserved: no gate is added.
 *
 * <p><b>Plain metrics.</b> The v1 scheduler kept no custom failure counter, so the
 * relay uses a plain {@link MicrometerOutboxMetrics} — there is no
 * {@code membership_outbox_publish_failures_total} to preserve. The standard
 * {@code membership.outbox.pending.count} gauge is added.
 *
 * <p>Topic resolution: ported VERBATIM from the v1
 * {@code MembershipOutboxPollingScheduler.resolveTopic} — iam topics are bare (no
 * {@code .v1} suffix); each {@code membership.*} event maps to its identically-named
 * topic. An unmapped eventType is rejected with {@link IllegalArgumentException}.
 */
@Component
public class MembershipOutboxPublisher extends AbstractOutboxPublisher<MembershipOutboxJpaEntity> {

    static final String TOPIC_SUBSCRIPTION_ACTIVATED = "membership.subscription.activated";
    static final String TOPIC_SUBSCRIPTION_EXPIRED = "membership.subscription.expired";
    static final String TOPIC_SUBSCRIPTION_CANCELLED = "membership.subscription.cancelled";

    public MembershipOutboxPublisher(MembershipOutboxJpaRepository repository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     TransactionTemplate transactionTemplate,
                                     Clock clock,
                                     MeterRegistry meterRegistry,
                                     @Value("${membership.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "membership"),
                clock,
                batchSize);

        Gauge.builder("membership.outbox.pending.count", repository,
                        MembershipOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished membership outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${membership.outbox.poll-ms:1000}",
            initialDelayString = "${membership.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return MembershipOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM
     * from the v1 {@code MembershipOutboxPollingScheduler.resolveTopic} (iam = bare
     * topic names, no {@code .v1} suffix), incl. reject-unmapped. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown membership event type: null");
        }
        return switch (eventType) {
            case "membership.subscription.activated" -> TOPIC_SUBSCRIPTION_ACTIVATED;
            case "membership.subscription.expired" -> TOPIC_SUBSCRIPTION_EXPIRED;
            case "membership.subscription.cancelled" -> TOPIC_SUBSCRIPTION_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown membership event type: " + eventType);
        };
    }
}
