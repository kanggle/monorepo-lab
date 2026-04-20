package com.wms.master.domain.event;

import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.UUID;

public record LotReactivatedEvent(
        UUID aggregateId,
        Lot snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LotReactivatedEvent from(Lot lot) {
        return new LotReactivatedEvent(
                lot.getId(),
                lot,
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "lot";
    }

    @Override
    public String eventType() {
        return "master.lot.reactivated";
    }
}
