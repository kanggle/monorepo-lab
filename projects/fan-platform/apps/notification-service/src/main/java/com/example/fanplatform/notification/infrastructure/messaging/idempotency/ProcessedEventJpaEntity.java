package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Consumer idempotency dedupe row (architecture.md § Idempotency). Keyed on the
 * envelope {@code eventId}; a duplicate eventId is skipped without creating a
 * second notification.
 *
 * <p>Same column shape as {@code libs:java-messaging}'s
 * {@code ProcessedEventJpaEntity} (event_id / event_type / processed_at).
 * Declared service-locally (not the lib entity) because this service excludes
 * {@code OutboxAutoConfiguration} — scanning the lib's {@code ProcessedEvent}
 * would also drag the lib's {@code OutboxJpaRepository}, whose outbox entity has
 * no table here (feedback §13; erp notification-service precedent).
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public static ProcessedEventJpaEntity create(String eventId, String eventType, Instant processedAt) {
        ProcessedEventJpaEntity entity = new ProcessedEventJpaEntity();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.processedAt = processedAt;
        return entity;
    }
}
