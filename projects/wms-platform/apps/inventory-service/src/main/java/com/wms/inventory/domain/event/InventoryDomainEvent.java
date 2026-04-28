package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for events that the application services hand to the outbox writer
 * after a state change commits. The serializer turns each event into a
 * {@code wms.inventory.*.v1} envelope per
 * {@code specs/contracts/events/inventory-events.md}.
 */
public sealed interface InventoryDomainEvent
        permits InventoryReceivedEvent, InventoryReservedEvent,
                InventoryReleasedEvent, InventoryConfirmedEvent,
                InventoryAdjustedEvent, InventoryTransferredEvent,
                InventoryLowStockDetectedEvent {

    /** Stable {@code eventType} string per the event contract, e.g., {@code inventory.received}. */
    String eventType();

    /** {@code aggregateType} field on the envelope. */
    String aggregateType();

    /** {@code aggregateId} field on the envelope (the event's primary aggregate id). */
    UUID aggregateId();

    /** {@code occurredAt} — the DB transaction commit time supplied by the caller. */
    Instant occurredAt();

    /** {@code actorId} — JWT subject for REST flows; {@code system:<consumer>} for events. */
    String actorId();

    /** Kafka partition key per topic layout (typically {@code locationId}). */
    String partitionKey();
}
