package com.example.account.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consume-path idempotency record — account-service owns its {@code processed_events}
 * table (Flyway {@code V0005__create_processed_events.sql}).
 *
 * <p>TASK-MONO-406 moved this out of {@code libs/java-messaging}. Per ADR-MONO-004 the
 * shared library ships the {@code EventDedupePort} contract; the entity, the table and
 * the repository scan belong to the service. V0005's own comment records why the table
 * was created — "Hibernate schema-validation requires this table because
 * ProcessedEventJpaEntity is auto-scanned via OutboxJpaConfig whenever java-messaging is
 * on the classpath" — which is exactly the fleet-wide coupling this task removes. The
 * migration is applied and immutable; only the mapping moves.
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
