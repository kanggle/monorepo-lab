package com.wms.inbound.adapter.out.persistence.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity backing {@code erp_webhook_dedupe}. Append-only — the V7 trigger
 * rejects UPDATE/DELETE.
 */
@Entity
@Table(name = "erp_webhook_dedupe")
public class ErpWebhookDedupeJpaEntity {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ErpWebhookDedupeJpaEntity() {
    }

    public ErpWebhookDedupeJpaEntity(String eventId, Instant receivedAt) {
        this.eventId = eventId;
        this.receivedAt = receivedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
