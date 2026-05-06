package com.example.scmplatform.inventoryvisibility.application.port.outbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for event idempotency checking and recording (T8).
 */
public interface EventDedupePort {

    /** Returns true if the event has already been processed. */
    boolean isDuplicate(UUID eventId);

    /** Records the event as processed. Called after successful processing. */
    void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic);
}
