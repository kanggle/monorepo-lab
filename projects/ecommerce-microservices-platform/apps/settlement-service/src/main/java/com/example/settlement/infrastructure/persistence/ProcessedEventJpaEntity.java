package com.example.settlement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Locally-owned dedupe row (terminal consumer — settlement does not import the libs
 * {@code processed_events} table to avoid pulling outbox auto-config). One row per
 * processed envelope {@code event_id}.
 */
@Entity
@Table(name = "processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    static ProcessedEventJpaEntity create(String eventId, String eventType) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.eventId = eventId;
        e.eventType = eventType;
        e.processedAt = Instant.now();
        return e;
    }
}
