package com.example.finance.ledger.infrastructure.outbox;

import com.example.messaging.outbox.AbstractOutboxPublisher;
import com.example.messaging.outbox.MicrometerOutboxMetrics;
import com.example.messaging.outbox.SpringDataOutboxRowRepository;
import com.example.messaging.outbox.TopicResolver;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ledger-service outbox relay (3rd increment, TASK-FIN-BE-009 — the GL/AP feed).
 *
 * <p>Thin wrapper around the shared {@link AbstractOutboxPublisher}
 * ({@code libs/java-messaging}, ADR-MONO-004 — the {@code OutboxRow} path). The
 * poll loop, exponential backoff, Kafka send, and mark-as-published live in the
 * lib; this class supplies the ledger specifics (the {@code ledger_outbox} table
 * via {@link LedgerOutboxJpaRepository}, the topic mapping, the {@code ledger}
 * metric prefix, the schedule cadence). It reuses the EXISTING
 * {@code KafkaTemplate<String,String>} bean (already present for the
 * {@code @RetryableTopic} DLT plumbing) — no second producer.
 *
 * <p>Topic resolution: the outbox row's {@code eventType} is already the fully
 * dotted {@code finance.ledger.entry.posted} / {@code finance.ledger.period.closed}
 * name, so the topic is just {@code <eventType>.v1} (the {@code .v1} version
 * suffix), per {@code finance-ledger-events.md} § Published — emitted.
 *
 * <p>Background polling is gated off by {@code ledger.outbox.polling.enabled=false}
 * so {@code @WebMvcTest} / unit runs never start a Kafka-less scheduler; the
 * integration suite enables it. The gate defaults ON ({@code matchIfMissing}) so
 * production runs the relay without extra config.
 */
@Component
@ConditionalOnProperty(value = "ledger.outbox.polling.enabled", havingValue = "true",
        matchIfMissing = true)
public class LedgerOutboxPublisher extends AbstractOutboxPublisher<LedgerOutboxJpaEntity> {

    public LedgerOutboxPublisher(LedgerOutboxJpaRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 TransactionTemplate transactionTemplate,
                                 Clock clock,
                                 MeterRegistry meterRegistry,
                                 @Value("${ledger.outbox.batch-size:100}") int batchSize) {
        super(SpringDataOutboxRowRepository.wrap(
                        repository,
                        repository::findPending,
                        repository::countByPublishedAtIsNull),
                kafkaTemplate,
                transactionTemplate,
                topicResolver(),
                new MicrometerOutboxMetrics(meterRegistry, "ledger"),
                clock,
                batchSize);

        Gauge.builder("ledger.outbox.pending.count", repository,
                        LedgerOutboxJpaRepository::countByPublishedAtIsNull)
                .description("Unpublished ledger outbox rows")
                .register(meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${ledger.outbox.polling-interval-ms:500}",
            initialDelayString = "${ledger.outbox.initial-delay-ms:2000}")
    public void publishPending() {
        super.publishPending();
    }

    /**
     * {@code finance.ledger.X → finance.ledger.X.v1}. The outbox row already
     * carries the fully dotted event type, so the topic is just the {@code .v1}
     * version-suffixed name.
     */
    private static TopicResolver topicResolver() {
        return eventType -> eventType + ".v1";
    }

    /** Topic-resolution helper exposed for tests. */
    static String topicFor(String eventType) {
        return topicResolver().resolveTopic(eventType);
    }
}
