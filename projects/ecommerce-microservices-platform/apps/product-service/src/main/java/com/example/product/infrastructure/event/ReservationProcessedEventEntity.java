package com.example.product.infrastructure.event;

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
 * Idempotent-consumer dedupe for the reservation saga consumers (TASK-BE-428). Keyed by the
 * inbound envelope {@code event_id}. A service-local table (not {@code libs/java-messaging})
 * mirroring {@code wms_processed_event}: originally to avoid pulling in the lib's
 * {@code OutboxAutoConfiguration} (product-service publishes to Kafka directly, not via the
 * outbox). TASK-MONO-406 deleted that auto-config together with the library's own dedupe
 * entity/repository, making service-local ownership the only shape there is — per
 * ADR-MONO-004 the shared library ships the {@code EventDedupePort} contract, while the
 * entity, the table and the repository scan belong to the service.
 */
@Entity
@Table(name = "reservation_processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationProcessedEventEntity {

    @Id
    @Column(name = "event_id", columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static ReservationProcessedEventEntity of(UUID eventId, String eventType, Instant now) {
        ReservationProcessedEventEntity e = new ReservationProcessedEventEntity();
        e.eventId = eventId;
        e.eventType = eventType;
        e.processedAt = now;
        return e;
    }
}
