package com.wms.inbound.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AsnReceivedEvent(
        UUID asnId,
        String asnNo,
        String source,
        UUID supplierPartnerId,
        String supplierPartnerCode,
        UUID warehouseId,
        LocalDate expectedArriveDate,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Line(
            UUID asnLineId,
            int lineNo,
            UUID skuId,
            String skuCode,
            UUID lotId,
            int expectedQty
    ) {}

    @Override
    public UUID aggregateId() { return asnId; }

    @Override
    public String aggregateType() { return "asn"; }

    @Override
    public String eventType() { return "inbound.asn.received"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
