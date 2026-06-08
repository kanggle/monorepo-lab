package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a {@code outbound.picking.requested} cannot be reserved because
 * the requested quantity exceeds availability on one or more lines (the negative
 * counterpart of {@link InventoryReservedEvent}).
 *
 * <p>TASK-MONO-196 (ADR-MONO-022 §D4): the dedicated reservation-failure signal
 * that drives the outbound auto-backorder. Deliberately a separate event — NOT
 * overloaded onto {@code inventory.adjusted}, whose topic
 * ({@code wms.inventory.adjusted.v1}) is a stock-mutation event consumed
 * cross-project by scm {@code inventory-visibility-service}.
 *
 * <p>No {@code Reservation} exists on failure, so {@link #aggregateId()} is the
 * {@code pickingRequestId} (also the partition key — outbound resolves the
 * {@code sagaId} from it).
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §4a.
 */
public record InventoryReserveFailedEvent(
        UUID pickingRequestId,
        String reason,
        List<Line> insufficientLines,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryReserveFailedEvent {
        if (insufficientLines == null || insufficientLines.isEmpty()) {
            throw new IllegalArgumentException("InventoryReserveFailedEvent must have at least one line");
        }
        insufficientLines = List.copyOf(insufficientLines);
    }

    @Override public String eventType() { return "inventory.reserve.failed"; }
    @Override public String aggregateType() { return "reservation"; }
    @Override public UUID aggregateId() { return pickingRequestId; }
    @Override public String partitionKey() { return pickingRequestId.toString(); }

    public record Line(
            UUID inventoryId,
            UUID skuId,
            UUID lotId,
            UUID locationId,
            int qtyRequested,
            int qtyAvailable
    ) {
    }
}
