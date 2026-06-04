package com.example.erp.readmodel.domain.dedupe;

import java.time.Instant;
import java.util.Objects;

/**
 * Consumer idempotency record (T8). Keyed by the envelope {@code eventId}; the
 * presence of a record means the event has been applied, so re-delivery is
 * skipped without mutation (the projection stays byte-identical). Captures the
 * read-model's processing provenance (there is no {@code audit_log} — read-only,
 * E5): {@code eventId} + {@code topic} + {@code aggregateId} + {@code processedAt}.
 *
 * <p>Pure Java — no framework annotations (Hexagonal domain).
 */
public final class EventDedupeRecord {

    private final String eventId;
    private final String topic;
    private final String aggregateId;
    private final Instant processedAt;

    public EventDedupeRecord(String eventId, String topic, String aggregateId, Instant processedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
    }

    public static EventDedupeRecord of(String eventId, String topic, String aggregateId,
                                       Instant processedAt) {
        return new EventDedupeRecord(eventId, topic, aggregateId, processedAt);
    }

    public String eventId() { return eventId; }
    public String topic() { return topic; }
    public String aggregateId() { return aggregateId; }
    public Instant processedAt() { return processedAt; }
}
