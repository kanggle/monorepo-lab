package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AsnClosedEvent(
        UUID asnId,
        String asnNo,
        UUID warehouseId,
        Instant closedAt,
        String closedBy,
        Summary summary,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Summary(
            int expectedTotal,
            int passedTotal,
            int damagedTotal,
            int shortTotal,
            int putawayConfirmedTotal,
            int discrepancyCount
    ) {}

    @Override
    public UUID aggregateId() { return asnId; }

    @Override
    public String aggregateType() { return "asn"; }

    @Override
    public String eventType() { return "inbound.asn.closed"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
