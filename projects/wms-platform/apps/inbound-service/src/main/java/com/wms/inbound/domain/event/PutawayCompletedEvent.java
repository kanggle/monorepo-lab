package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cross-service contract event consumed by {@code inventory-service}. Fired
 * exactly once per {@code PutawayInstruction} when the last pending line is
 * resolved (CONFIRMED or SKIPPED). Only confirmed lines populate
 * {@link #lines()} — skipped lines are absent.
 */
public record PutawayCompletedEvent(
        UUID putawayInstructionId,
        UUID asnId,
        UUID warehouseId,
        Instant completedAt,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Line(
            UUID putawayConfirmationId,
            UUID skuId,
            UUID lotId,
            UUID locationId,
            int qtyReceived
    ) {}

    @Override
    public UUID aggregateId() { return putawayInstructionId; }

    @Override
    public String aggregateType() { return "putaway_instruction"; }

    @Override
    public String eventType() { return "inbound.putaway.completed"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
