package com.example.order.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consume-path idempotency record — order-service owns its {@code processed_events}
 * table (Flyway {@code V6__create_processed_events_table.sql}).
 *
 * <p>TASK-MONO-406 moved this out of {@code libs/java-messaging}. Per ADR-MONO-004
 * the shared library ships the {@code EventDedupePort} contract; the entity, the
 * table and the repository scan belong to the service. A library that registers a
 * JPA entity into every consumer's context claims the bean name
 * {@code processedEventJpaRepository} fleet-wide and breaks the boot of any service
 * that models the same concept (TASK-BE-432, TASK-BE-461).
 *
 * <p>{@code LocalDateTime} (not {@code Instant}) is deliberate and unchanged from the
 * library original: the writer here and the reader in
 * {@link com.example.order.infrastructure.event.ProcessedEventCleanupScheduler} share
 * one host zone, so the retention window is self-consistent. Switching to
 * {@code Instant} would be a schema-affecting change with no defect to fix.
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEventJpaEntity create(String eventId, String eventType) {
        ProcessedEventJpaEntity entity = new ProcessedEventJpaEntity();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.processedAt = LocalDateTime.now();
        return entity;
    }
}
