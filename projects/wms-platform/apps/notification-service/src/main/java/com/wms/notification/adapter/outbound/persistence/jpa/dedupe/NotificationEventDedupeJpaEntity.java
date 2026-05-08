package com.wms.notification.adapter.outbound.persistence.jpa.dedupe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code notification_event_dedupe}. Append-only — never
 * updated after the initial insert. Duplicate eventId collides on the PK
 * constraint and is reported up to the {@link
 * com.wms.notification.application.port.outbound.AlertDedupePort}
 * implementation.
 */
@Entity
@Table(name = "notification_event_dedupe")
public class NotificationEventDedupeJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, length = 120)
    private String sourceTopic;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(nullable = false, length = 16)
    private String outcome;

    protected NotificationEventDedupeJpaEntity() {
    }

    public NotificationEventDedupeJpaEntity(UUID eventId, String sourceTopic,
                                            Instant processedAt, String outcome) {
        this.eventId = eventId;
        this.sourceTopic = sourceTopic;
        this.processedAt = processedAt;
        this.outcome = outcome;
    }

    public UUID getEventId() { return eventId; }
    public String getSourceTopic() { return sourceTopic; }
    public Instant getProcessedAt() { return processedAt; }
    public String getOutcome() { return outcome; }
}
