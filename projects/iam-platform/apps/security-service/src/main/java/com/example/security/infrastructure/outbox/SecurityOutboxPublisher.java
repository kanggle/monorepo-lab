package com.example.security.infrastructure.outbox;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaEntity;
import com.example.security.infrastructure.persistence.SecurityOutboxJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * security-service outbox relay (TASK-BE-453 — outbox v1 → v2). A thin wrapper
 * around the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring the in-worktree
 * auth-service's {@code AuthOutboxPublisher} + finance account-service's
 * {@code AccountOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the security
 * specifics: the {@code security_outbox} table via
 * {@link SecurityOutboxJpaRepository}, the topic mapping, the {@code security}
 * metric prefix, and the schedule cadence. Replaces the v1
 * {@code SecurityOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>No gate.</b> The v1 {@code SecurityOutboxPollingScheduler} was an
 * unconditional {@code @Component} with no {@code @ConditionalOnProperty} — the
 * relay was always active ({@code @EnableScheduling} on
 * {@code SecurityApplication}). That is preserved: no gate is added.
 *
 * <p><b>Plain metrics.</b> The v1 scheduler kept no custom failure counter, so the
 * relay uses a plain {@link MicrometerOutboxMetrics} — there is no
 * {@code security_outbox_publish_failures_total} to preserve. The standard
 * {@code security.outbox.pending.count} gauge is added.
 *
 * <p>Topic resolution: ported VERBATIM from the v1
 * {@code SecurityOutboxPollingScheduler.resolveTopic} — iam topics are bare (no
 * {@code .v1} suffix); each {@code security.*} event maps to its identically-named
 * topic. An unmapped eventType is rejected with {@link IllegalArgumentException}.
 */
@Component
public class SecurityOutboxPublisher extends AbstractOutboxPublisher<SecurityOutboxJpaEntity> {

    static final String TOPIC_SUSPICIOUS_DETECTED = "security.suspicious.detected";
    static final String TOPIC_AUTO_LOCK_TRIGGERED = "security.auto.lock.triggered";
    static final String TOPIC_AUTO_LOCK_PENDING = "security.auto.lock.pending";
    // TASK-BE-258: GDPR compliance audit trail topic
    static final String TOPIC_PII_MASKED = "security.pii.masked";

    public SecurityOutboxPublisher(SecurityOutboxJpaRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   TransactionTemplate transactionTemplate,
                                   Clock clock,
                                   MeterRegistry meterRegistry,
                                   @Value("${security.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "security"),
                clock,
                batchSize);

        Gauge.builder("security.outbox.pending.count", repository,
                        SecurityOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished security outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${security.outbox.poll-ms:1000}",
            initialDelayString = "${security.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return SecurityOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM
     * from the v1 {@code SecurityOutboxPollingScheduler.resolveTopic} (iam = bare
     * topic names, no {@code .v1} suffix), incl. reject-unmapped. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown security event type: null");
        }
        return switch (eventType) {
            case "security.suspicious.detected" -> TOPIC_SUSPICIOUS_DETECTED;
            case "security.auto.lock.triggered" -> TOPIC_AUTO_LOCK_TRIGGERED;
            case "security.auto.lock.pending" -> TOPIC_AUTO_LOCK_PENDING;
            case "security.pii.masked" -> TOPIC_PII_MASKED;
            default -> throw new IllegalArgumentException("Unknown security event type: " + eventType);
        };
    }
}
