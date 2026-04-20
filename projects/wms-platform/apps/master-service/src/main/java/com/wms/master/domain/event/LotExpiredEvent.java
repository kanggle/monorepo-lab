package com.wms.master.domain.event;

import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by the scheduled lot-expiration batch. Per
 * {@code specs/contracts/events/master-events.md} §6.5, this event carries:
 * <ul>
 *   <li>{@code actorId = null} — the originator is the scheduler, not a user
 *   <li>{@code triggeredBy = "scheduled-job:lot-expiry"}
 *   <li>{@code scheduledAt} — when the batch was initiated (ISO-8601 UTC)
 * </ul>
 *
 * <p>Actor-aware consumers must tolerate {@code actorId = null} (the event
 * envelope schema in {@code contracts/events/event-envelope.schema.json}
 * already declares the field as {@code ["string", "null"]}).
 */
public record LotExpiredEvent(
        UUID aggregateId,
        Lot snapshot,
        String triggeredBy,
        Instant scheduledAt,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static final String TRIGGERED_BY = "scheduled-job:lot-expiry";

    /**
     * Build the event from an expired Lot snapshot. {@code actorId} is
     * intentionally {@code null} (system-originated).
     */
    public static LotExpiredEvent from(Lot lot, Instant scheduledAt) {
        return new LotExpiredEvent(
                lot.getId(),
                lot,
                TRIGGERED_BY,
                scheduledAt,
                lot.getUpdatedAt(),
                null);
    }

    @Override
    public String aggregateType() {
        return "lot";
    }

    @Override
    public String eventType() {
        return "master.lot.expired";
    }
}
