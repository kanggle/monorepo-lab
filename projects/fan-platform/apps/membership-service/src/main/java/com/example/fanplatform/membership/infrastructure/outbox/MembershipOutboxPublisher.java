package com.example.fanplatform.membership.infrastructure.outbox;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaEntity;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
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
 * membership-service outbox publisher (TASK-FAN-BE-020, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff (1s → 2s → … → 30s cap) and the
 * {@code eventId}/{@code eventType} record headers all live in
 * {@code libs/java-messaging}. Replaces the v1
 * {@code MembershipOutboxPollingScheduler extends OutboxPollingScheduler}.
 * Supplies the membership-service specifics:
 * <ul>
 *   <li>the {@code membership_outbox} repository via
 *       {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping the three {@code fan.membership.*} event
 *       types to their existing {@code ….v1} topics (ported verbatim from the v1
 *       {@code MembershipOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code membership} prefix,
 *       <b>wrapped</b> so a publish failure ALSO increments the pre-existing
 *       {@code membership_outbox_publish_failures_total} counter — preserving the
 *       v1 {@code onKafkaSendFailure} hook (dashboard/alert continuity)</li>
 *   <li>a {@code membership.outbox.pending.count} gauge (new in v2)</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>The v1 relay was an unconditional {@code @Component} (no
 * {@code @ConditionalOnProperty}); this preserves that — the relay bean always
 * exists. The poll/initial-delay knobs are read from {@code membership.outbox.*}.
 */
@Component
public class MembershipOutboxPublisher extends AbstractOutboxPublisher<MembershipOutboxJpaEntity> {

    static final String TOPIC_ACTIVATED = "fan.membership.activated.v1";
    static final String TOPIC_CANCELED = "fan.membership.canceled.v1";
    static final String TOPIC_EXPIRED = "fan.membership.expired.v1";

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
                failurePreservingMetrics(meterRegistry),
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
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code MembershipOutboxPollingScheduler.resolveTopic}, incl.
     * reject-unmapped (treated as a non-retryable poison-pill row by the publisher
     * loop). Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown membership event type: null");
        }
        return switch (eventType) {
            case MembershipEventPublisher.EVENT_ACTIVATED -> TOPIC_ACTIVATED;
            case MembershipEventPublisher.EVENT_CANCELED -> TOPIC_CANCELED;
            case MembershipEventPublisher.EVENT_EXPIRED -> TOPIC_EXPIRED;
            default -> throw new IllegalArgumentException("Unknown membership event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code membership_outbox_publish_failures_total} counter
     * (preserving the v1 {@code MembershipOutboxPollingScheduler.onKafkaSendFailure}
     * hook). The v1 scheduler fired that hook only on a real send failure (a row
     * with a known eventType), so the wrapper guards on {@code eventType != null}
     * (poll-level failures, which the lib reports with a null eventType, are not
     * counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "membership");
        Counter publishFailures = Counter.builder("membership_outbox_publish_failures_total")
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
