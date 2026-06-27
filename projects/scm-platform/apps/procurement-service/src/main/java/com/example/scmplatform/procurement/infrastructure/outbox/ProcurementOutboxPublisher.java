package com.example.scmplatform.procurement.infrastructure.outbox;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.OutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaEntity;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaRepository;
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
 * procurement-service outbox publisher (TASK-SCM-BE-032, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5) — the polling loop, Kafka send,
 * mark-as-published, exponential backoff (1s → 2s → … → 30s cap) and the
 * {@code eventId}/{@code eventType} record headers all live in
 * {@code libs/java-messaging}. Replaces the v1
 * {@code ProcurementOutboxPollingScheduler extends OutboxPollingScheduler}.
 * Supplies the procurement-service specifics:
 * <ul>
 *   <li>the {@code procurement_outbox} repository via
 *       {@link SpringDataOutboxRowRepository#wrap}</li>
 *   <li>a {@link TopicResolver} mapping the seven {@code scm.procurement.*} event
 *       types to their existing {@code …​.v1} topics (ported verbatim from the v1
 *       {@code OutboxPollingScheduler.resolveTopic}, incl. reject-unmapped)</li>
 *   <li>a {@link MicrometerOutboxMetrics} with the {@code procurement} prefix,
 *       <b>wrapped</b> so a publish failure ALSO increments the pre-existing
 *       {@code procurement_outbox_publish_failures_total} counter — preserving the
 *       v1 {@code onKafkaSendFailure} hook (dashboard/alert continuity)</li>
 *   <li>a {@code procurement.outbox.pending.count} gauge (new in v2)</li>
 *   <li>the {@code @Scheduled} trigger driving {@link #publishPending()}</li>
 * </ul>
 *
 * <p>{@code @ConditionalOnProperty("outbox.polling.enabled")} preserves the v1
 * gate name exactly — slice/unit tests set {@code outbox.polling.enabled=false}.
 */
@Component
@ConditionalOnProperty(value = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class ProcurementOutboxPublisher extends AbstractOutboxPublisher<ProcurementOutboxJpaEntity> {

    static final String TOPIC_PO_SUBMITTED = "scm.procurement.po.submitted.v1";
    static final String TOPIC_PO_ACKNOWLEDGED = "scm.procurement.po.acknowledged.v1";
    static final String TOPIC_PO_CONFIRMED = "scm.procurement.po.confirmed.v1";
    static final String TOPIC_PO_CANCELED = "scm.procurement.po.canceled.v1";
    static final String TOPIC_PO_RECEIVED = "scm.procurement.po.received.v1";
    static final String TOPIC_PO_CLOSED = "scm.procurement.po.closed.v1";
    static final String TOPIC_ASN_RECEIVED = "scm.procurement.asn.received.v1";

    public ProcurementOutboxPublisher(ProcurementOutboxJpaRepository repository,
                                      KafkaTemplate<String, String> kafkaTemplate,
                                      TransactionTemplate transactionTemplate,
                                      Clock clock,
                                      MeterRegistry meterRegistry,
                                      @Value("${procurement.outbox.batch-size:100}") int batchSize) {
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

        Gauge.builder("procurement.outbox.pending.count", repository,
                        ProcurementOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished procurement outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${procurement.outbox.poll-ms:1000}",
            initialDelayString = "${procurement.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return ProcurementOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code OutboxPollingScheduler.resolveTopic}, incl. reject-unmapped
     * (treated as a non-retryable poison-pill row by the publisher loop).
     * Exposed package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown procurement event type: null");
        }
        return switch (eventType) {
            case ProcurementEventPublisher.EVENT_PO_SUBMITTED -> TOPIC_PO_SUBMITTED;
            case ProcurementEventPublisher.EVENT_PO_ACKNOWLEDGED -> TOPIC_PO_ACKNOWLEDGED;
            case ProcurementEventPublisher.EVENT_PO_CONFIRMED -> TOPIC_PO_CONFIRMED;
            case ProcurementEventPublisher.EVENT_PO_CANCELED -> TOPIC_PO_CANCELED;
            case ProcurementEventPublisher.EVENT_PO_RECEIVED -> TOPIC_PO_RECEIVED;
            case ProcurementEventPublisher.EVENT_PO_CLOSED -> TOPIC_PO_CLOSED;
            case ProcurementEventPublisher.EVENT_ASN_RECEIVED -> TOPIC_ASN_RECEIVED;
            default -> throw new IllegalArgumentException(
                    "Unknown procurement event type: " + eventType);
        };
    }

    /**
     * Wraps {@link MicrometerOutboxMetrics} so a per-event publish failure also
     * increments the v1 {@code procurement_outbox_publish_failures_total} counter
     * (preserving the v1 {@code ProcurementOutboxPollingScheduler.onKafkaSendFailure}
     * hook). The v1 scheduler fired that hook only on a real send failure (a row
     * with a known eventType), so the wrapper guards on {@code eventType != null}
     * (poll-level failures, which the lib reports with a null eventType, are not
     * counted — matching v1).
     */
    private static OutboxMetrics failurePreservingMetrics(MeterRegistry registry) {
        MicrometerOutboxMetrics base = new MicrometerOutboxMetrics(registry, "procurement");
        Counter publishFailures = Counter.builder("procurement_outbox_publish_failures_total")
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
