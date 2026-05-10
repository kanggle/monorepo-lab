package com.example.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;
import java.util.UUID;

/**
 * Reference JPA mapping for an outbox row implementing {@link OutboxRow}.
 *
 * <p>Declared as {@link MappedSuperclass} so each service's concrete entity
 * can extend it and bind to its own table name (e.g. {@code <service>_outbox}).
 * Services that already have a custom outbox entity implement {@link OutboxRow}
 * directly without inheriting this class.
 *
 * <p>Schema columns:
 * <pre>
 *   event_id        UUID PRIMARY KEY,
 *   event_type      VARCHAR(100) NOT NULL,
 *   aggregate_type  VARCHAR(60)  NOT NULL,
 *   aggregate_id    VARCHAR(60)  NOT NULL,
 *   partition_key   VARCHAR(60),
 *   payload         JSONB        NOT NULL,
 *   occurred_at     TIMESTAMP    NOT NULL,
 *   published_at    TIMESTAMP,
 *   retries         INT          NOT NULL DEFAULT 0,
 *   last_error      TEXT
 * </pre>
 *
 * <p>Subclasses declare only the {@code @Entity @Table(name = "...")} annotations
 * and (optionally) override {@code getPayload()} if the column type differs.
 *
 * <p>This class is not annotated {@code @Entity} so it does not participate in
 * Hibernate's entity scanning by itself.
 */
@MappedSuperclass
public abstract class OutboxRowEntity implements OutboxRow {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    protected UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    protected String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    protected String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 60)
    protected String aggregateId;

    @Column(name = "partition_key", length = 60)
    protected String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    protected String payload;

    @Column(name = "occurred_at", nullable = false)
    protected Instant occurredAt;

    @Column(name = "published_at")
    protected Instant publishedAt;

    @Column(name = "retries", nullable = false)
    protected int retries;

    @Column(name = "last_error", columnDefinition = "TEXT")
    protected String lastError;

    protected OutboxRowEntity() {
    }

    protected OutboxRowEntity(UUID eventId, String eventType, String aggregateType,
                              String aggregateId, String partitionKey, String payload,
                              Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    @Override
    public UUID getEventId() { return eventId; }

    @Override
    public String getEventType() { return eventType; }

    @Override
    public String getAggregateType() { return aggregateType; }

    @Override
    public String getAggregateId() { return aggregateId; }

    @Override
    public String getPartitionKey() { return partitionKey; }

    @Override
    public String getPayload() { return payload; }

    @Override
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public Instant getPublishedAt() { return publishedAt; }

    @Override
    public int getRetries() { return retries; }

    @Override
    public String getLastError() { return lastError; }

    @Override
    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    @Override
    public void recordFailure(String error) {
        this.retries++;
        this.lastError = error;
    }
}
