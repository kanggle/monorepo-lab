package com.wms.admin.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Service-local outbox JPA entity. {@code payload} is JSONB —
 * {@code @JdbcTypeCode(SqlTypes.JSON)} required (TASK-SCM-INT-001b root cause #2).
 */
@Entity
@Table(name = "admin_outbox")
public class AdminOutboxJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "event_version", nullable = false, length = 10)
    private String eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "partition_key", nullable = false, length = 120)
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected AdminOutboxJpaEntity() {
    }

    public AdminOutboxJpaEntity(UUID id, String aggregateType, String aggregateId,
                                String eventType, String eventVersion, String payload,
                                String partitionKey, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.partitionKey = partitionKey;
        this.createdAt = createdAt;
        this.attemptCount = 0;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getEventVersion() { return eventVersion; }
    public String getPayload() { return payload; }
    public String getPartitionKey() { return partitionKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttemptCount() { return attemptCount; }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }
}
