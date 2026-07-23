package com.example.scmplatform.logistics.application.port.outbound;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for consumer idempotency (T8) — the {@code processed_events} table.
 *
 * <p>SCAFFOLD: the seam consumer that calls this is wired in TASK-SCM-BE-044. The port +
 * adapter land now so BE-044 introduces no new persistence.
 */
public interface ProcessedEventPort {

    boolean isDuplicate(UUID eventId);

    void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic);
}
