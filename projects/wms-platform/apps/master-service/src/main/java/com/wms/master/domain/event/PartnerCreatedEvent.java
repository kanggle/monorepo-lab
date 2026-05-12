package com.wms.master.domain.event;

import com.wms.master.domain.model.Partner;
import java.time.Instant;
import java.util.UUID;

public record PartnerCreatedEvent(
        UUID aggregateId,
        Partner snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static PartnerCreatedEvent from(Partner partner) {
        return new PartnerCreatedEvent(
                partner.getId(),
                partner,
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "partner";
    }

    @Override
    public String eventType() {
        return "master.partner.created";
    }
}
