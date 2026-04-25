package com.wms.master.adapter.out.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Micrometer metrics for the master-service outbox.
 * <p>
 * Names match {@code specs/contracts/events/master-events.md} § Producer Guarantees.
 */
public class OutboxMetrics {

    public static final String PENDING_COUNT = "master.outbox.pending.count";
    public static final String PUBLISH_FAILURE_TOTAL = "master.outbox.publish.failure.total";
    public static final String PUBLISH_SUCCESS_TOTAL = "master.outbox.publish.success.total";

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;
    private final Counter publishFailureCounter;
    private final Counter publishSuccessCounter;

    public OutboxMetrics(MeterRegistry meterRegistry, TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
        Gauge.builder(PENDING_COUNT, this, OutboxMetrics::pendingCount)
                .strongReference(true)
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder(PUBLISH_FAILURE_TOTAL)
                .description("Outbox rows that failed to publish to Kafka")
                .register(meterRegistry);
        this.publishSuccessCounter = Counter.builder(PUBLISH_SUCCESS_TOTAL)
                .description("Outbox rows successfully published to Kafka")
                .register(meterRegistry);
    }

    public void recordPublishFailure() {
        publishFailureCounter.increment();
    }

    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    /**
     * Gauge callback invoked by Micrometer on every scrape — usually outside a
     * transactional context (e.g., Prometheus scrape thread). Wrapping the JPA
     * query in a read-only {@link TransactionTemplate} ensures the shared
     * EntityManager proxy has a live session even on async scrape threads, and
     * swallows any late-boot race where the persistence unit is not yet ready.
     */
    private double pendingCount() {
        if (entityManager == null || transactionTemplate == null) {
            return 0d;
        }
        try {
            Double value = transactionTemplate.execute(status -> {
                Number count = (Number) entityManager
                        .createQuery("SELECT COUNT(o) FROM OutboxJpaEntity o WHERE o.status = 'PENDING'")
                        .getSingleResult();
                return count.doubleValue();
            });
            return value == null ? 0d : value;
        } catch (RuntimeException ex) {
            // Micrometer surfaces gauge exceptions by dropping the metric from
            // the scrape; callers then see a broken /actuator/prometheus
            // response. Log-and-zero instead so the scrape stays consistent.
            log.warn("Failed to read outbox pending count gauge", ex);
            return 0d;
        }
    }
}
