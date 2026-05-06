package com.example.scmplatform.inventoryvisibility.domain.dedupe;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency record for Kafka event processing (T8 — transactional trait).
 * Keyed by eventId (UUID v7 from wms-platform envelope).
 * <p>
 * Before processing any wms event, the consumer checks if an EventDedupeRecord
 * exists for the eventId. If it does, the event is a duplicate and is skipped.
 */
public class EventDedupeRecord {

    private final UUID eventId;
    private final String tenantId;
    private final Instant processedAt;
    private final String sourceTopic;

    public EventDedupeRecord(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
        this.sourceTopic = Objects.requireNonNull(sourceTopic, "sourceTopic");
    }

    public static EventDedupeRecord of(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {
        return new EventDedupeRecord(eventId, tenantId, processedAt, sourceTopic);
    }

    public UUID getEventId() { return eventId; }
    public String getTenantId() { return tenantId; }
    public Instant getProcessedAt() { return processedAt; }
    public String getSourceTopic() { return sourceTopic; }
}
