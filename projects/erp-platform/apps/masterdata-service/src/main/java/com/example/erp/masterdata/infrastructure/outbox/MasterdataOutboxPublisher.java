package com.example.erp.masterdata.infrastructure.outbox;

import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaEntity;
import com.example.erp.masterdata.infrastructure.persistence.jpa.MasterdataOutboxJpaRepository;
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
 * masterdata-service outbox relay (TASK-ERP-BE-026 — outbox v1 → v2). A thin
 * wrapper around the shared {@link AbstractOutboxPublisher}
 * ({@code libs/java-messaging}, ADR-MONO-004 § 5 — the {@code OutboxRow} path),
 * mirroring finance account-service's {@code AccountOutboxPublisher} + scm
 * procurement-service's {@code ProcurementOutboxPublisher}. The poll loop,
 * exponential backoff, Kafka send and mark-as-published live in the lib; this
 * class supplies the masterdata specifics: the {@code masterdata_outbox} table via
 * {@link MasterdataOutboxJpaRepository}, the topic mapping, the {@code masterdata}
 * metric prefix + the preserved {@code masterdata_outbox_publish_failures_total}
 * counter, and the schedule cadence. Replaces the v1
 * {@code MasterdataOutboxPollingScheduler extends OutboxPollingScheduler}.
 *
 * <p>{@code @ConditionalOnProperty("outbox.polling.enabled")} preserves the v1
 * gate name exactly — slice/unit tests set {@code outbox.polling.enabled=false};
 * the gate defaults ON ({@code matchIfMissing}).
 */
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class MasterdataOutboxPublisher extends AbstractOutboxPublisher<MasterdataOutboxJpaEntity> {

    static final String TOPIC_DEPARTMENT_CHANGED = "erp.masterdata.department.changed.v1";
    static final String TOPIC_EMPLOYEE_CHANGED = "erp.masterdata.employee.changed.v1";
    static final String TOPIC_JOBGRADE_CHANGED = "erp.masterdata.jobgrade.changed.v1";
    static final String TOPIC_COSTCENTER_CHANGED = "erp.masterdata.costcenter.changed.v1";
    static final String TOPIC_BUSINESSPARTNER_CHANGED = "erp.masterdata.businesspartner.changed.v1";

    public MasterdataOutboxPublisher(MasterdataOutboxJpaRepository repository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     TransactionTemplate transactionTemplate,
                                     Clock clock,
                                     MeterRegistry meterRegistry,
                                     @Value("${masterdata.outbox.batch-size:100}") int batchSize) {
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

        Gauge.builder("masterdata.outbox.pending.count", repository,
                        MasterdataOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished masterdata outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${masterdata.outbox.poll-ms:1000}",
            initialDelayString = "${masterdata.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return MasterdataOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code MasterdataOutboxPollingScheduler.resolveTopic}, incl.
     * reject-unmapped (treated as a non-retryable poison-pill row by the publisher
     * loop). Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown masterdata event type: null");
        }
        return switch (eventType) {
            case MasterdataEventPublisher.EVENT_DEPARTMENT_CHANGED -> TOPIC_DEPARTMENT_CHANGED;
            case MasterdataEventPublisher.EVENT_EMPLOYEE_CHANGED -> TOPIC_EMPLOYEE_CHANGED;
            case MasterdataEventPublisher.EVENT_JOBGRADE_CHANGED -> TOPIC_JOBGRADE_CHANGED;
            case MasterdataEventPublisher.EVENT_COSTCENTER_CHANGED -> TOPIC_COSTCENTER_CHANGED;
            case MasterdataEventPublisher.EVENT_BUSINESSPARTNER_CHANGED -> TOPIC_BUSINESSPARTNER_CHANGED;
            default -> throw new IllegalArgumentException(
                    "Unknown masterdata event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code masterdata_outbox_publish_failures_total} counter
     * (preserving the v1 {@code MasterdataOutboxPollingScheduler.onKafkaSendFailure}
     * hook — name + description verbatim). The v1 scheduler fired that hook only on
     * a real send failure (a row with a known eventType), so the wrapper guards on
     * {@code eventType != null} (poll-level failures, which the lib reports with a
     * null eventType, are not counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "masterdata");
        Counter publishFailures = Counter.builder("masterdata_outbox_publish_failures_total")
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
