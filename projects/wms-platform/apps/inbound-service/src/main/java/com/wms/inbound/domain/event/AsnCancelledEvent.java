package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AsnCancelledEvent(
        UUID asnId,
        String asnNo,
        String previousStatus,
        String reason,
        Instant cancelledAt,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    @Override
    public UUID aggregateId() { return asnId; }

    @Override
    public String aggregateType() { return "asn"; }

    @Override
    public String eventType() { return "inbound.asn.cancelled"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
