package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity backing {@code erp_order_webhook_dedupe}. Append-only — the V8
 * role grant rejects UPDATE/DELETE.
 */
@Entity
@Table(name = "erp_order_webhook_dedupe")
public class ErpOrderWebhookDedupe {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ErpOrderWebhookDedupe() {
    }

    public ErpOrderWebhookDedupe(String eventId, String source, Instant receivedAt) {
        this.eventId = eventId;
        this.source = source;
        this.receivedAt = receivedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSource() {
        return source;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
