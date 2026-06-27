package com.example.erp.approval.infrastructure.outbox;

import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * approval-service outbox relay (TASK-ERP-BE-025 — outbox v1 → v2). A thin
 * wrapper around the shared {@link AbstractOutboxPublisher}
 * ({@code libs/java-messaging}, ADR-MONO-004 § 5 — the {@code OutboxRow} path),
 * mirroring finance account-service's {@code AccountOutboxPublisher} + scm
 * procurement-service's {@code ProcurementOutboxPublisher}. The poll loop,
 * exponential backoff, Kafka send and mark-as-published live in the lib; this
 * class supplies the approval specifics: the {@code approval_outbox} table via
 * {@link ApprovalOutboxJpaRepository}, the topic mapping, the {@code approval}
 * metric prefix + the preserved {@code approval_outbox_publish_failures_total}
 * counter, and the schedule cadence. Replaces the v1
 * {@code ApprovalOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p><b>Delegation-gap fix.</b> The v1 {@code ApprovalOutboxPollingScheduler}
 * mapped only four topics (submitted/approved/rejected/withdrawn) but
 * {@code ApprovalEventPublisher} ALSO emits {@code erp.approval.delegated} +
 * {@code erp.approval.delegation.revoked} (written to the outbox, then
 * poison-pilled by the v1 relay's reject-unmapped — a latent v1 head-of-line
 * blocking bug). The event contract {@code erp-approval-events.md} DOES define
 * {@code erp.approval.delegated.v1} + {@code erp.approval.delegation.revoked.v1},
 * so the v2 {@link #topicFor} maps ALL SIX — a contract-aligning fix.
 *
 * <p>{@code @ConditionalOnProperty("outbox.polling.enabled")} preserves the v1
 * gate name exactly — slice/unit tests set {@code outbox.polling.enabled=false};
 * the gate defaults ON ({@code matchIfMissing}).
 */
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalOutboxPublisher extends AbstractOutboxPublisher<ApprovalOutboxJpaEntity> {

    static final String TOPIC_APPROVAL_SUBMITTED = "erp.approval.submitted.v1";
    static final String TOPIC_APPROVAL_APPROVED = "erp.approval.approved.v1";
    static final String TOPIC_APPROVAL_REJECTED = "erp.approval.rejected.v1";
    static final String TOPIC_APPROVAL_WITHDRAWN = "erp.approval.withdrawn.v1";
    // Delegation-gap fix (TASK-ERP-BE-025): the v1 scheduler omitted these two,
    // poison-pilling delegated/revoked rows. The contract defines both topics.
    static final String TOPIC_APPROVAL_DELEGATED = "erp.approval.delegated.v1";
    static final String TOPIC_APPROVAL_DELEGATION_REVOKED = "erp.approval.delegation.revoked.v1";

    public ApprovalOutboxPublisher(ApprovalOutboxJpaRepository repository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   TransactionTemplate transactionTemplate,
                                   Clock clock,
                                   MeterRegistry meterRegistry,
                                   @Value("${approval.outbox.batch-size:100}") int batchSize) {
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

        Gauge.builder("approval.outbox.pending.count", repository,
                        ApprovalOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished approval outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${approval.outbox.poll-ms:1000}",
            initialDelayString = "${approval.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return ApprovalOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported from the v1
     * {@code ApprovalOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped
     * (treated as a non-retryable poison-pill row by the publisher loop), and
     * EXTENDED with the two delegation topics the v1 scheduler omitted
     * (delegation-gap fix — see class javadoc). Exposed package-private + static
     * for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown approval event type: null");
        }
        return switch (eventType) {
            case ApprovalEventPublisher.EVENT_APPROVAL_SUBMITTED -> TOPIC_APPROVAL_SUBMITTED;
            case ApprovalEventPublisher.EVENT_APPROVAL_APPROVED -> TOPIC_APPROVAL_APPROVED;
            case ApprovalEventPublisher.EVENT_APPROVAL_REJECTED -> TOPIC_APPROVAL_REJECTED;
            case ApprovalEventPublisher.EVENT_APPROVAL_WITHDRAWN -> TOPIC_APPROVAL_WITHDRAWN;
            case ApprovalEventPublisher.EVENT_APPROVAL_DELEGATED -> TOPIC_APPROVAL_DELEGATED;
            case ApprovalEventPublisher.EVENT_APPROVAL_DELEGATION_REVOKED ->
                    TOPIC_APPROVAL_DELEGATION_REVOKED;
            default -> throw new IllegalArgumentException(
                    "Unknown approval event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code approval_outbox_publish_failures_total} counter
     * (preserving the v1 {@code ApprovalOutboxPollingScheduler.onKafkaSendFailure}
     * hook — name + description verbatim). The v1 scheduler fired that hook only on
     * a real send failure (a row with a known eventType), so the wrapper guards on
     * {@code eventType != null} (poll-level failures, which the lib reports with a
     * null eventType, are not counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "approval");
        Counter publishFailures = Counter.builder("approval_outbox_publish_failures_total")
                .description("Outbox events that failed to publish to Kafka.")
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
