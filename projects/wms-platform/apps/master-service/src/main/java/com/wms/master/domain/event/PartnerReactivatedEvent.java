package com.wms.master.domain.event;

import com.wms.master.domain.model.Partner;
import java.time.Instant;
import java.util.UUID;

public record PartnerReactivatedEvent(
        UUID aggregateId,
        Partner snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static PartnerReactivatedEvent from(Partner partner) {
        return new PartnerReactivatedEvent(
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
        return "master.partner.reactivated";
    }
}
