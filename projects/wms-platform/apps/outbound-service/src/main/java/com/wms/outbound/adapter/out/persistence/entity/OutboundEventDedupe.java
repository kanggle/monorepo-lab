package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code outbound_event_dedupe}. Append-only; never updated
 * after the initial insert (W2 trigger enforces this at the DB level).
 *
 * <p>Columns align with {@code specs/services/outbound-service/domain-model.md} §8:
 * <ul>
 *   <li>{@code event_id} — UUID PK; the Kafka record's event-id header</li>
 *   <li>{@code event_type} — dotted event name (e.g. {@code inventory.reserved})</li>
 *   <li>{@code processed_at} — wall-clock at first-seen processing</li>
 *   <li>{@code outcome} — {@code APPLIED} / {@code IGNORED_DUPLICATE} / {@code FAILED};
 *       ops visibility for replay investigations</li>
 * </ul>
 */
@Entity
@Table(name = "outbound_event_dedupe")
public class OutboundEventDedupe {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * §8: outcome — {@code APPLIED}, {@code IGNORED_DUPLICATE}, or {@code FAILED}.
     * Nullable for rows written before V9 migration; never null for new rows.
     */
    @Column(name = "outcome", length = 30)
    private String outcome;

    protected OutboundEventDedupe() {
    }

    /** Legacy constructor: outcome is null (rows written before V9). */
    public OutboundEventDedupe(UUID eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    /** Full constructor: includes outcome for all new rows. */
    public OutboundEventDedupe(UUID eventId, String eventType, Instant processedAt, String outcome) {
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
