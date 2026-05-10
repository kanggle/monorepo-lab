package com.example.messaging.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "published_at", columnDefinition = "TIMESTAMP")
    private Instant publishedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public static OutboxJpaEntity create(String aggregateType, String aggregateId,
                                         String eventType, String payload) {
        OutboxJpaEntity entity = new OutboxJpaEntity();
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.createdAt = Instant.now();
        entity.status = "PENDING";
        return entity;
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
    }

    /**
     * Transition this row to the {@code FAILED} terminal state — used when the
     * publish failure is permanent (e.g. unknown {@code eventType} that no
     * resolver matches, unserializable payload). FAILED rows are excluded from
     * subsequent {@code findPendingWithLock} polls so they no longer block the
     * batch drain. The terminal timestamp is captured via {@code publishedAt}.
     *
     * <p>The failure reason is recorded at the call site (via {@code log.error}
     * by {@code OutboxPollingScheduler.sendToKafka}); operators correlate a
     * FAILED row with logs by {@code eventType} + {@code aggregateId} +
     * {@code publishedAt}. Persisting the reason on-row was deferred to keep
     * this lib evolution backwards-compatible with existing service Flyway
     * schemas (no new column required).
     */
    public void markFailed() {
        this.status = "FAILED";
        this.publishedAt = Instant.now();
    }
}
