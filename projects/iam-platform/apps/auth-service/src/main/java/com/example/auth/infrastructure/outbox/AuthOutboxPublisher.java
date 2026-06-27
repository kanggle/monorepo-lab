package com.example.auth.infrastructure.outbox;

import com.example.auth.infrastructure.persistence.AuthOutboxJpaEntity;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaRepository;
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
 * auth-service outbox relay (TASK-BE-450 — outbox v1 → v2). A thin wrapper around
 * the shared {@link AbstractOutboxPublisher} ({@code libs/java-messaging},
 * ADR-MONO-004 § 5 — the {@code OutboxRow} path), mirroring finance
 * account-service's {@code AccountOutboxPublisher} + erp approval-service's
 * {@code ApprovalOutboxPublisher}. The poll loop, exponential backoff, Kafka send
 * and mark-as-published live in the lib; this class supplies the auth specifics:
 * the {@code auth_outbox} table via {@link AuthOutboxJpaRepository}, the topic
 * mapping, the {@code auth} metric prefix, and the schedule cadence. Replaces the
 * v1 {@code AuthOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>No gate.</b> The v1 {@code AuthOutboxPollingScheduler} was an
 * unconditional {@code @Component} with no {@code @ConditionalOnProperty} — the
 * relay was always active ({@code @EnableScheduling} on {@code AuthApplication}).
 * That is preserved: no gate is added.
 *
 * <p><b>Plain metrics.</b> The v1 scheduler kept no custom failure counter (it
 * never overrode {@code onKafkaSendFailure} with a metric), so the relay uses a
 * plain {@link MicrometerOutboxMetrics} — there is no
 * {@code auth_outbox_publish_failures_total} to preserve. The standard
 * {@code auth.outbox.pending.count} gauge is added.
 *
 * <p>Topic resolution: ported VERBATIM from the v1
 * {@code AuthOutboxPollingScheduler.resolveTopic} — iam topics are bare (no
 * {@code .v1} suffix); each {@code auth.*} event maps to its identically-named
 * topic. An unmapped eventType is rejected with {@link IllegalArgumentException}.
 */
@Component
public class AuthOutboxPublisher extends AbstractOutboxPublisher<AuthOutboxJpaEntity> {

    static final String TOPIC_LOGIN_ATTEMPTED = "auth.login.attempted";
    static final String TOPIC_LOGIN_FAILED = "auth.login.failed";
    static final String TOPIC_LOGIN_SUCCEEDED = "auth.login.succeeded";
    static final String TOPIC_TOKEN_REFRESHED = "auth.token.refreshed";
    static final String TOPIC_TOKEN_REUSE_DETECTED = "auth.token.reuse.detected";
    static final String TOPIC_TOKEN_TENANT_MISMATCH = "auth.token.tenant.mismatch";
    static final String TOPIC_SESSION_CREATED = "auth.session.created";
    static final String TOPIC_SESSION_REVOKED = "auth.session.revoked";

    public AuthOutboxPublisher(AuthOutboxJpaRepository repository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               TransactionTemplate transactionTemplate,
                               Clock clock,
                               MeterRegistry meterRegistry,
                               @Value("${auth.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "auth"),
                clock,
                batchSize);

        Gauge.builder("auth.outbox.pending.count", repository,
                        AuthOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished auth outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${auth.outbox.poll-ms:1000}",
            initialDelayString = "${auth.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return AuthOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported VERBATIM
     * from the v1 {@code AuthOutboxPollingScheduler.resolveTopic} (iam = bare
     * topic names, no {@code .v1} suffix), incl. reject-unmapped. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown auth event type: null");
        }
        return switch (eventType) {
            case "auth.login.attempted" -> TOPIC_LOGIN_ATTEMPTED;
            case "auth.login.failed" -> TOPIC_LOGIN_FAILED;
            case "auth.login.succeeded" -> TOPIC_LOGIN_SUCCEEDED;
            case "auth.token.refreshed" -> TOPIC_TOKEN_REFRESHED;
            case "auth.token.reuse.detected" -> TOPIC_TOKEN_REUSE_DETECTED;
            case "auth.token.tenant.mismatch" -> TOPIC_TOKEN_TENANT_MISMATCH;
            case "auth.session.created" -> TOPIC_SESSION_CREATED;
            case "auth.session.revoked" -> TOPIC_SESSION_REVOKED;
            default -> throw new IllegalArgumentException("Unknown auth event type: " + eventType);
        };
    }
}
