package com.wms.master.domain.event;

import com.wms.master.domain.model.Partner;
import java.time.Instant;
import java.util.UUID;

public record PartnerDeactivatedEvent(
        UUID aggregateId,
        Partner snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static PartnerDeactivatedEvent from(Partner partner, String reason) {
        return new PartnerDeactivatedEvent(
                partner.getId(),
                partner,
                reason,
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "partner";
    }

    @Override
    public String eventType() {
        return "master.partner.deactivated";
    }
}
