package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PutawayInstructedEvent(
        UUID putawayInstructionId,
        UUID asnId,
        UUID warehouseId,
        String plannedBy,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Line(
            UUID putawayLineId,
            UUID asnLineId,
            UUID skuId,
            UUID lotId,
            UUID destinationLocationId,
            String destinationLocationCode,
            int qtyToPutaway
    ) {}

    @Override
    public UUID aggregateId() { return putawayInstructionId; }

    @Override
    public String aggregateType() { return "putaway_instruction"; }

    @Override
    public String eventType() { return "inbound.putaway.instructed"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
