package com.wms.inbound.adapter.out.persistence.outbox;

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
 * JPA entity for {@code inbound_outbox}. Implements the shared
 * {@link OutboxRow} contract (TASK-MONO-049 + ADR-MONO-004) so the generic
 * {@code AbstractOutboxPublisher} in {@code libs/java-messaging} can drive
 * this table.
 */
@Entity
@Table(name = "inbound_outbox")
public class InboundOutboxJpaEntity implements OutboxRow {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "event_version", nullable = false, length = 10)
    private String eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "partition_key", nullable = false, length = 60)
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected InboundOutboxJpaEntity() {}

    public InboundOutboxJpaEntity(UUID id, String aggregateType, UUID aggregateId,
                                   String eventType, String eventVersion,
                                   String payload, String partitionKey, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.partitionKey = partitionKey;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    @Override public String getAggregateType() { return aggregateType; }
    /** Native UUID accessor — the {@link OutboxRow} contract returns the String form. */
    public UUID getAggregateUuid() { return aggregateId; }
    @Override public String getEventType() { return eventType; }
    public String getEventVersion() { return eventVersion; }
    @Override public String getPayload() { return payload; }
    @Override public String getPartitionKey() { return partitionKey; }
    public Instant getCreatedAt() { return createdAt; }
    @Override public Instant getPublishedAt() { return publishedAt; }

    @Override
    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    // --- OutboxRow contract adapters (TASK-MONO-049) -------------------------

    @Override
    public UUID getEventId() {
        return id;
    }

    @Override
    public String getAggregateId() {
        return aggregateId == null ? null : aggregateId.toString();
    }

    @Override
    public Instant getOccurredAt() {
        return createdAt;
    }
}
