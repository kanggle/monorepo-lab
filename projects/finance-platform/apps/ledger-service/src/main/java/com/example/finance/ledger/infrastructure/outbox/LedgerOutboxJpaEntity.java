package com.example.finance.ledger.infrastructure.outbox;

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
 * Per-service transactional-outbox row for ledger-service (3rd increment,
 * TASK-FIN-BE-009 — the GL/AP feed). Implements the shared {@link OutboxRow}
 * contract ({@code libs/java-messaging}, ADR-MONO-004) so the generic
 * {@link com.example.messaging.outbox.AbstractOutboxPublisher} relay can drive
 * this table without depending on this entity.
 *
 * <p><b>Why a per-service outbox row (ADR-MONO-004).</b> The dedupe/outbox tables of a
 * service belong to the service, not to the shared library. Historically the libs
 * {@code OutboxAutoConfiguration} entity-scanned a library
 * {@code ProcessedEventJpaEntity} (also mapped to {@code processed_events}) into every
 * consumer, which collided with ledger-service's OWN consumer-dedupe entity —
 * TASK-MONO-406 deleted that auto-config and the library entity/repository outright, so
 * {@code libs/java-messaging} now ships no {@code @Entity} and there is nothing left to
 * exclude ({@code OutboxMetricsAutoConfiguration}, which still exists, remains excluded:
 * this service supplies its own outbox failure handling). ledger owns this
 * {@code ledger_outbox} table; the consumer-dedupe path is untouched.
 *
 * <p><b>MySQL mapping.</b> {@code payload} is a {@code TEXT} column (the
 * account-service {@code outbox.payload TEXT} MySQL precedent — NOT Postgres
 * {@code jsonb}; the wms reference entity is Postgres). The {@code id} primary key
 * is a {@link UUID} mapped to a {@code CHAR(36)} column ({@link JdbcTypeCode}
 * {@link SqlTypes#CHAR}) — Hibernate serialises the UUID as its 36-char canonical
 * string, so the entity field type and the DDL column type are unambiguous and
 * match exactly (the {@code V3} migration declares {@code id CHAR(36)}). The id is
 * UUIDv7 ({@code com.example.common.id.UuidV7}) so it doubles as the natural
 * created-order sort key.
 *
 * <p>By the time a row reaches this entity, {@link #payload} is already the
 * serialised canonical-envelope JSON string (built by
 * {@code OutboxLedgerEventPublisher}); the relay never parses it.
 */
@Entity
@Table(name = "ledger_outbox")
public class LedgerOutboxJpaEntity implements OutboxRow {

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

    @Column(name = "event_version", nullable = false, length = 10)
    private String eventVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "partition_key", nullable = false, length = 64)
    private String partitionKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected LedgerOutboxJpaEntity() {
    }

    private LedgerOutboxJpaEntity(UUID id, String aggregateType, String aggregateId,
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

    /** Factory — the append-side publisher constructs a pending row to persist. */
    public static LedgerOutboxJpaEntity create(UUID id, String aggregateType, String aggregateId,
                                               String eventType, String eventVersion,
                                               String payload, String partitionKey,
                                               Instant createdAt) {
        return new LedgerOutboxJpaEntity(id, aggregateType, aggregateId, eventType,
                eventVersion, payload, partitionKey, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEventVersion() {
        return eventVersion;
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
