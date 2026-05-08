package com.wms.notification.application.port.outbound;

import com.wms.notification.domain.delivery.DedupeOutcome;
import java.util.UUID;

/**
 * Out-port for consumer-side eventId dedupe (T8). Implementations write an
 * insert-only row in {@code notification_event_dedupe} that participates in
 * the caller's outer transaction.
 */
public interface AlertDedupePort {

    /** Result of an {@link #recordIfAbsent} call. */
    enum Result {
        /** Row was inserted — caller should proceed with side effects. */
        INSERTED,
        /** A row with the same {@code eventId} already exists — caller should exit. */
        DUPLICATE
    }

    /**
     * Insert a dedupe row, signalling the caller whether this eventId has
     * already been observed.
     *
     * @param eventId     UUIDv7 from envelope
     * @param sourceTopic Kafka topic the event arrived on
     * @param outcome     classification recorded for replay diagnostics
     */
    Result recordIfAbsent(UUID eventId, String sourceTopic, DedupeOutcome outcome);

    /** {@code true} iff a row already exists for {@code eventId}. Read-only. */
    boolean exists(UUID eventId);
}
