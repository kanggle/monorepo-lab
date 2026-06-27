package com.example.settlement.infrastructure.event;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.infrastructure.persistence.SettlementOutboxEntity;
import com.example.settlement.infrastructure.persistence.SettlementOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * settlement-service outbox publisher (TASK-BE-447, outbox v2).
 *
 * <p>A thin wrapper around the shared {@link AbstractOutboxPublisher}
 * (TASK-MONO-049 / ADR-MONO-004 § 5). Supplies the settlement-service specifics:
 * the {@code settlement_outbox} repository, a {@link TopicResolver} mapping the
 * single event type {@code settlement.period.closed.v1} → topic
 * {@code settlement.period.closed} (ported verbatim from the v1
 * {@code SettlementOutboxPollingScheduler.resolveTopic}, incl. reject-unmapped), a
 * {@link MicrometerOutboxMetrics} with the {@code settlement} prefix + a preserved
 * {@code settlement.outbox.pending.count} gauge, and the {@code @Scheduled} trigger.
 *
 * <p>{@code @Profile("!standalone")} — the standalone (H2, no Kafka) profile has no
 * relay (matching the v1 scheduler); the write path is replaced there by
 * {@code NoopSettlementEventPublisher} (no outbox row written).
 */
@Component
@Profile("!standalone")
public class SettlementOutboxPublisher extends AbstractOutboxPublisher<SettlementOutboxEntity> {

    static final String TOPIC_PERIOD_CLOSED = "settlement.period.closed";

    public SettlementOutboxPublisher(SettlementOutboxRepository repository,
                                     KafkaTemplate<String, String> kafkaTemplate,
                                     TransactionTemplate transactionTemplate,
                                     Clock clock,
                                     MeterRegistry meterRegistry,
                                     @Value("${settlement.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "settlement"),
                clock,
                batchSize);

        Gauge.builder("settlement.outbox.pending.count", repository,
                        SettlementOutboxRepository::countByPublishedAtIsNull)
                .description("Unpublished settlement outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${settlement.outbox.poll-ms:1000}",
            initialDelayString = "${settlement.outbox.initial-delay-ms:5000}")
    public void publishPending() {
        super.publishPending();
    }

    private static TopicResolver topicResolver() {
        return SettlementOutboxPublisher::topicFor;
    }

    /**
     * Resolves the outbox row's event type to its Kafka topic. Ported verbatim from
     * the v1 {@code SettlementOutboxPollingScheduler.resolveTopic} — the single
     * published type {@code settlement.period.closed.v1} maps to
     * {@code settlement.period.closed}; anything else is rejected. Exposed
     * package-private + static for the unit test.
     */
    static String topicFor(String eventType) {
        if (SettlementPeriodClosedEvent.EVENT_TYPE.equals(eventType)) {
            return TOPIC_PERIOD_CLOSED;
        }
        throw new IllegalArgumentException("Unknown event type: " + eventType);
    }
}
