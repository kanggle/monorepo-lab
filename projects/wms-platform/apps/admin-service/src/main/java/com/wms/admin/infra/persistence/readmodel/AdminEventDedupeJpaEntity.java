package com.wms.admin.infra.persistence.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code admin_event_dedupe} (table created in V1 by BE-045).
 * Mostly insert-only; the only documented mutation path is
 * {@code outcome = IGNORED_DUPLICATE_LATE} when a fresh eventId arrives with a
 * stale {@code occurredAt} (idempotency.md § 2.3).
 */
@Entity
@Table(name = "admin_event_dedupe")
public class AdminEventDedupeJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(nullable = false, length = 30)
    private String outcome;

    protected AdminEventDedupeJpaEntity() {
    }

    public AdminEventDedupeJpaEntity(UUID eventId, String eventType, Instant processedAt,
                                     String outcome) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.outcome = outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
    public String getOutcome() { return outcome; }
}
