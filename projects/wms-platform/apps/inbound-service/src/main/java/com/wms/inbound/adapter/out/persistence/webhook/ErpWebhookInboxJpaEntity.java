package com.wms.inbound.adapter.out.persistence.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity backing {@code erp_webhook_inbox}.
 *
 * <p>Status moves {@code PENDING → APPLIED} (or {@code FAILED}) by the
 * background processor. The raw JSON payload is stored verbatim as JSONB.
 */
@Entity
@Table(name = "erp_webhook_inbox")
public class ErpWebhookInboxJpaEntity {

    @Id
    @Column(name = "event_id", length = 80)
    private String eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "signature", nullable = false, length = 100)
    private String signature;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected ErpWebhookInboxJpaEntity() {
    }

    public ErpWebhookInboxJpaEntity(String eventId, String rawPayload, String signature,
                                    String source, Instant receivedAt, String status) {
        this.eventId = eventId;
        this.rawPayload = rawPayload;
        this.signature = signature;
        this.source = source;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getSignature() {
        return signature;
    }

    public String getSource() {
        return source;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void markApplied(Instant at) {
        this.status = "APPLIED";
        this.processedAt = at;
        this.failureReason = null;
    }

    public void markFailed(Instant at, String reason) {
        this.status = "FAILED";
        this.processedAt = at;
        this.failureReason = reason;
    }
}
