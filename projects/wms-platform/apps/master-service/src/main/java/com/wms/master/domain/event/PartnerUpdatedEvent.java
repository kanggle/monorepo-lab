package com.wms.master.domain.event;

import com.wms.master.domain.model.Partner;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartnerUpdatedEvent(
        UUID aggregateId,
        Partner snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static PartnerUpdatedEvent from(Partner partner, List<String> changedFields) {
        return new PartnerUpdatedEvent(
                partner.getId(),
                partner,
                List.copyOf(changedFields),
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "partner";
    }

    @Override
    public String eventType() {
        return "master.partner.updated";
    }
}
