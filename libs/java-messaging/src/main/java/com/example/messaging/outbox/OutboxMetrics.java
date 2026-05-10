package com.example.messaging.outbox;

import java.time.Duration;

/**
 * Observability contract for outbox-publisher metrics. Implementations route
 * to the chosen meter backend (Micrometer is the canonical reference, see
 * {@link MicrometerOutboxMetrics}).
 *
 * <p>The contract intentionally exposes only behaviours, not metric names —
 * each service tags the underlying counter/gauge/timer with its own service
 * label so the resulting metric names follow the {@code <service>.outbox.*}
 * naming convention without forcing it through this interface.
 */
public interface OutboxMetrics {

    /**
     * Record a successful Kafka publish (Kafka ACK received and the row was
     * marked published). Implementations typically increment a counter and may
     * record the publish-to-ACK lag from {@code occurredAt}.
     *
     * @param eventType dotted event type for tag/dimension
     * @param lag       wall time between {@code OutboxRow.occurredAt} and Kafka ACK
     */
    void recordPublishSuccess(String eventType, Duration lag);

    /**
     * Record a Kafka publish failure (send threw or timed out). Implementations
     * typically increment a counter tagged by {@code eventType} and reason.
     *
     * @param eventType dotted event type
     * @param reason    short description (typically {@code Throwable.getClass().getSimpleName()})
     */
    void recordPublishFailure(String eventType, String reason);
}
