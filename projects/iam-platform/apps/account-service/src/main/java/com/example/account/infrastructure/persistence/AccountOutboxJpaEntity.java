package com.example.account.infrastructure.persistence;

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
 * Per-service transactional-outbox row for account-service (TASK-BE-451 — outbox
 * v1 → v2). Implements the shared {@link OutboxRow} contract
 * ({@code libs/java-messaging}, ADR-MONO-004 § 5) so the generic
 * {@link com.example.messaging.outbox.AbstractOutboxPublisher} relay can drive
 * this table without depending on this entity. Mirrors finance account-service's
 * {@code AccountOutboxJpaEntity} (the MySQL v2 precedent) and erp approval-service's
 * {@code ApprovalOutboxJpaEntity}, minus the {@code event_version} column (the iam
 * v1 path never carried one).
 *
 * <p><b>MySQL mapping.</b> {@code payload} is a {@code TEXT} column (NOT Postgres
 * {@code jsonb}); {@code id} is a {@link UUID} mapped to {@code CHAR(36)} via
 * {@link JdbcTypeCode}{@code (}{@link SqlTypes#CHAR}{@code )} — Hibernate
 * serialises the UUID as its 36-char canonical string, matching the {@code V0026}
 * migration's {@code id CHAR(36)}.
 *
 * <p><b>FLAT wire (NOT a 7-field envelope).</b> account-service's v1 publishers used
 * {@code BaseEventPublisher.saveEvent} (serialize-as-is, NO envelope), so by the time
 * a row reaches this entity {@link #payload} is the bare payload map JSON — the EXACT
 * v1 on-wire Kafka value (TASK-BE-422/423 locked the flat shape). The relay never
 * parses it.
 *
 * <p>Lives under {@code com.example.account.infrastructure.persistence} (the
 * {@code JpaConfig} {@code @EntityScan} / {@code @EnableJpaRepositories} base) so it
 * registers — a missing scan would only fail the full-boot IT, never a mock-repo unit
 * test (payment §27 lesson).
 */
@Entity
@Table(name = "account_outbox")
public class AccountOutboxJpaEntity implements OutboxRow {

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

    protected AccountOutboxJpaEntity() {
    }

    private AccountOutboxJpaEntity(UUID id, String aggregateType, String aggregateId,
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
    public static AccountOutboxJpaEntity create(UUID id, String aggregateType, String aggregateId,
                                                String eventType, String payload,
                                                String partitionKey, Instant createdAt) {
        return new AccountOutboxJpaEntity(id, aggregateType, aggregateId, eventType,
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
