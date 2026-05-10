package com.wms.outbound.adapter.out.persistence.entity;

import com.example.messaging.outbox.OutboxRow;
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
 * <p>Implements the shared {@link OutboxRow} contract (TASK-MONO-049 + ADR-MONO-004)
 * so the generic {@code AbstractOutboxPublisher} in {@code libs/java-messaging} can
 * drive this table without taking a hard dependency on the entity class.
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
public class OutboundOutboxEntity implements OutboxRow {

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

    @Override
    public String getAggregateType() {
        return aggregateType;
    }

    /** Native UUID accessor for callers that operate in UUID space. */
    public UUID getAggregateUuid() {
        return aggregateId;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    public String getEventVersion() {
        return eventVersion;
    }

    @Override
    public String getPayload() {
        return payload;
    }

    @Override
    public String getPartitionKey() {
        return partitionKey;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public void markPublished(Instant at) {
        this.publishedAt = at;
        this.status = "PUBLISHED";
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    // --- OutboxRow contract adapters (TASK-MONO-049) ---------------------------

    /**
     * The shared contract names the unique key {@code eventId}; this entity
     * stores it as {@code id}. Both refer to the same UUIDv7 column.
     */
    @Override
    public UUID getEventId() {
        return id;
    }

    /**
     * The shared contract returns {@code aggregateId} as String for
     * project-agnostic reasons; this entity uses {@link UUID} natively. The
     * String form is the {@code toString()} of the UUID.
     */
    @Override
    public String getAggregateId() {
        return aggregateId == null ? null : aggregateId.toString();
    }

    /**
     * The shared contract calls the domain timestamp {@code occurredAt}; this
     * entity stores the same value under {@code created_at} (write timestamp).
     */
    @Override
    public Instant getOccurredAt() {
        return createdAt;
    }

    @Override
    public int getRetries() {
        return retryCount;
    }
}
