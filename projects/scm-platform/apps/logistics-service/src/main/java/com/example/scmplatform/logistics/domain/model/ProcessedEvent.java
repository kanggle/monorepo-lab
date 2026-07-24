package com.example.scmplatform.logistics.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumer idempotency marker (T8) for the {@code outbound.shipping.confirmed} seam —
 * the envelope {@code eventId} that has already been processed.
 *
 * <p>SCAFFOLD: the {@code ShippingConfirmedConsumer} that records these is wired in
 * TASK-SCM-BE-044. The domain type + {@code processed_events} table land now so BE-044 adds
 * no migration. Framework-free POJO.
 */
public record ProcessedEvent(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {

    public ProcessedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");
        Objects.requireNonNull(sourceTopic, "sourceTopic must not be null");
    }
}
