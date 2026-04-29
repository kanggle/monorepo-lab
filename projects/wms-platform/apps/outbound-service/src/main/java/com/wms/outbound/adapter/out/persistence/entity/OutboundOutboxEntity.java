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
 * JPA entity backing {@code outbound_outbox}.
 *
 * <p>Columns align with {@code specs/services/outbound-service/domain-model.md} §7:
 * <ul>
 *   <li>{@code aggregate_type} — {@code ORDER} / {@code OUTBOUND_SAGA} / {@code SHIPMENT}</li>
 *   <li>{@code aggregate_id} — the aggregate's UUID</li>
 *   <li>{@code event_type} — dotted event name (e.g. {@code outbound.order.received})</li>
 *   <li>{@code event_version} — schema version, e.g. {@code v1}</li>
 *   <li>{@code payload} — JSONB per outbound-events.md</li>
 *   <li>{@code partition_key} — {@code saga_id} for saga events; {@code order_id} for
 *       order lifecycle events. Used for Kafka partition routing.</li>
 *   <li>{@code created_at} / {@code published_at} — write / publish timestamps</li>
 * </ul>
 *
 * <p>Publisher-tracking columns ({@code status}, {@code retry_count}) are
 * implementation detail added by V6 for the outbox publisher (TASK-BE-036). They are
 * not part of the logical §7 definition but are retained here and documented.
 */
@Entity
@Table(name = "outbound_outbox")
public class OutboundOutboxEntity {

    @Id
    private UUID id;

    /** §7: aggregate_type — "ORDER" / "OUTBOUND_SAGA" / "SHIPMENT". */
    @Column(name = "aggregate_type", length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** §7: event_version — "v1". */
    @Column(name = "event_version", length = 10)
    private String eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /**
     * §7: partition_key — saga_id for saga events; order_id for order lifecycle events.
     * Used as Kafka record key for ordered delivery within a saga.
     */
    @Column(name = "partition_key", length = 60)
    private String partitionKey;

    /** Publisher-tracking: PENDING / PUBLISHED. Added by V6 (publisher impl detail). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Publisher-tracking: retry attempt counter. Added by V6 (publisher impl detail). */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected OutboundOutboxEntity() {
    }

    public OutboundOutboxEntity(UUID id, UUID aggregateId, String eventType,
                                String payload, String status, Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    public OutboundOutboxEntity(UUID id, String aggregateType, UUID aggregateId,
                                String eventType, String eventVersion, String payload,
                                String partitionKey, String status, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.partitionKey = partitionKey;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventVersion() {
        return eventVersion;
    }

    public String getPayload() {
        return payload;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
