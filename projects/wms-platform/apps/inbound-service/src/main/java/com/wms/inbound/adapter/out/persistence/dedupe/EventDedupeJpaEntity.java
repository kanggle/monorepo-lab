package com.wms.inbound.adapter.out.persistence.dedupe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code inbound_event_dedupe}. Append-only; never updated
 * after the initial insert. The PK ({@code event_id}) is supplied by the caller
 * — duplicate eventIds collide on the PK constraint and are reported up.
 */
@Entity
@Table(name = "inbound_event_dedupe")
public class EventDedupeJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    protected EventDedupeJpaEntity() {
    }

    public EventDedupeJpaEntity(UUID eventId, String eventType, Instant processedAt, String outcome) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.outcome = outcome;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getOutcome() {
        return outcome;
    }
}
