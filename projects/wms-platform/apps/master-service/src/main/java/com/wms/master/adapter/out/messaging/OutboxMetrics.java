package com.wms.master.adapter.out.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Micrometer metrics for the master-service outbox.
 * <p>
 * Names match {@code specs/contracts/events/master-events.md} § Producer Guarantees.
 */
public class OutboxMetrics {

    public static final String PENDING_COUNT = "master.outbox.pending.count";
    public static final String PUBLISH_FAILURE_TOTAL = "master.outbox.publish.failure.total";
    public static final String PUBLISH_SUCCESS_TOTAL = "master.outbox.publish.success.total";

    @PersistenceContext
    private EntityManager entityManager;

    private final Counter publishFailureCounter;
    private final Counter publishSuccessCounter;

    public OutboxMetrics(MeterRegistry meterRegistry) {
        meterRegistry.gauge(PENDING_COUNT, Tags.empty(), this, OutboxMetrics::pendingCount);
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

    private double pendingCount() {
        if (entityManager == null) {
            return 0d;
        }
        Number count = (Number) entityManager
                .createQuery("SELECT COUNT(o) FROM OutboxJpaEntity o WHERE o.status = 'PENDING'")
                .getSingleResult();
        return count.doubleValue();
    }
}
