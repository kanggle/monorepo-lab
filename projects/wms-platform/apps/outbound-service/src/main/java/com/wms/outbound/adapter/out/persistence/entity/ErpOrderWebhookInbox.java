package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity backing {@code erp_order_webhook_inbox}.
 *
 * <p>Status moves {@code PENDING → APPLIED} (or {@code FAILED}) by the
 * background processor. The raw JSON payload is stored verbatim as JSONB.
 */
@Entity
@Table(name = "erp_order_webhook_inbox")
public class ErpOrderWebhookInbox {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 80)
    private String eventId;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ErpOrderWebhookInbox() {
    }

    public ErpOrderWebhookInbox(UUID id, String eventId, String source, String payload,
                                String status, Instant receivedAt) {
        this.id = id;
        this.eventId = eventId;
        this.source = source;
        this.payload = payload;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSource() {
        return source;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void markApplied(Instant at) {
        this.status = "APPLIED";
        this.processedAt = at;
    }

    public void markFailed(Instant at) {
        this.status = "FAILED";
        this.processedAt = at;
    }
}
