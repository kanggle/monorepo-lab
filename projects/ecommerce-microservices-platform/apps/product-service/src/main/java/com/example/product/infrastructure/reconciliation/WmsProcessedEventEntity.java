package com.example.product.infrastructure.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent-consumer dedupe (T8) for the wms reconciliation leg — product-service's
 * first inbound consumer. Keyed by the wms envelope {@code eventId}. A local table
 * (not {@code libs/java-messaging}) to avoid pulling in OutboxAutoConfiguration, which
 * product-service does not use.
 */
@Entity
@Table(name = "wms_processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WmsProcessedEventEntity {

    @Id
    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static WmsProcessedEventEntity of(UUID eventId, String eventType, Instant now) {
        WmsProcessedEventEntity e = new WmsProcessedEventEntity();
        e.eventId = eventId;
        e.eventType = eventType;
        e.processedAt = now;
        return e;
    }
}
