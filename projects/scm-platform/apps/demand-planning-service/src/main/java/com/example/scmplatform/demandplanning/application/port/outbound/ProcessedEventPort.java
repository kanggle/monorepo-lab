package com.example.scmplatform.demandplanning.application.port.outbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for consumer idempotency (T8) — processed_events table.
 */
public interface ProcessedEventPort {

    boolean isDuplicate(UUID eventId);

    void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic);
}
