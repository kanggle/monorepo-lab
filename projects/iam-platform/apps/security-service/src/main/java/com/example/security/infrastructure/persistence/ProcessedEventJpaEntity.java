package com.example.security.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consume-path idempotency record — security-service owns its {@code processed_events}
 * table (Flyway {@code V0003__create_processed_events.sql}), whose UNIQUE constraint on
 * {@code event_id} is the authoritative dedupe guard (the Redis fast path in
 * {@code EventDedupService} is advisory only).
 *
 * <p>TASK-MONO-406 moved this out of {@code libs/java-messaging}. Per ADR-MONO-004 the
 * shared library ships the {@code EventDedupePort} contract; the entity, the table and
 * the repository scan belong to the service.
 *
 * <p>{@code LocalDateTime} is unchanged from the library original — no reader compares it
 * against an {@code Instant}, so there is no timezone defect to fix here.
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
