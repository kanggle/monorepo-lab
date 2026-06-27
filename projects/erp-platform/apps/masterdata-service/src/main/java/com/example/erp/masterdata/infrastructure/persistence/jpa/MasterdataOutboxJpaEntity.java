package com.example.erp.masterdata.infrastructure.persistence.jpa;

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
 * Per-service transactional-outbox row for masterdata-service (TASK-ERP-BE-026 —
 * outbox v1 → v2). Implements the shared {@link OutboxRow} contract
 * ({@code libs/java-messaging}, ADR-MONO-004 § 5) so the generic
 * {@link com.example.messaging.outbox.AbstractOutboxPublisher} relay can drive
 * this table without depending on this entity. Mirrors finance account-service's
 * {@code AccountOutboxJpaEntity} (the MySQL v2 precedent), minus the
 * {@code event_version} column (the erp v1 envelope never carried one).
 *
 * <p><b>MySQL mapping.</b> {@code payload} is a {@code TEXT} column (NOT Postgres
 * {@code jsonb}); {@code id} is a {@link UUID} mapped to {@code CHAR(36)} via
 * {@link JdbcTypeCode}{@code (}{@link SqlTypes#CHAR}{@code )} — Hibernate
 * serialises the UUID as its 36-char canonical string, matching the {@code V2}
 * migration's {@code id CHAR(36)}. The id is UUIDv7
 * ({@code com.example.common.id.UuidV7}) so the outbox PK doubles as the
 * time-ordered created-order sort key.
 *
 * <p>By the time a row reaches this entity, {@link #payload} is already the
 * serialised v1 envelope JSON ({@code {eventId, eventType, source, occurredAt,
 * schemaVersion, partitionKey, payload}} — built by
 * {@code OutboxMasterdataEventPublisher}); the relay never parses it.
 *
 * <p>Lives under {@code com.example.erp.masterdata.infrastructure.persistence.jpa}
 * (the {@code JpaConfig} {@code @EntityScan} / {@code @EnableJpaRepositories}
 * base) so it registers — a missing scan would only fail the full-boot IT, never
 * a mock-repo unit test (payment §27 lesson).
 */
@Entity
@Table(name = "masterdata_outbox")
public class MasterdataOutboxJpaEntity implements OutboxRow {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "partition_key", nullable = false, length = 64)
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected MasterdataOutboxJpaEntity() {
    }

    private MasterdataOutboxJpaEntity(UUID id, String aggregateType, String aggregateId,
                                      String eventType, String payload, String partitionKey,
                                      Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.partitionKey = partitionKey;
        this.createdAt = createdAt;
    }

    /** Factory — the append-side publisher constructs a pending row to persist. */
    public static MasterdataOutboxJpaEntity create(UUID id, String aggregateType, String aggregateId,
                                                   String eventType, String payload,
                                                   String partitionKey, Instant createdAt) {
        return new MasterdataOutboxJpaEntity(id, aggregateType, aggregateId, eventType,
                payload, partitionKey, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // --- OutboxRow contract -------------------------------------------------

    @Override
    public UUID getEventId() {
        return id;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public String getAggregateType() {
        return aggregateType;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getPartitionKey() {
        return partitionKey;
    }

    @Override
    public String getPayload() {
        return payload;
    }

    @Override
    public Instant getOccurredAt() {
        return createdAt;
    }

    @Override
    public Instant getPublishedAt() {
        return publishedAt;
    }

    @Override
    public void markPublished(Instant at) {
        this.publishedAt = at;
    }
}
