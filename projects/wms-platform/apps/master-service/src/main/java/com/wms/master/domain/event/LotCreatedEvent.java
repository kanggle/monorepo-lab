package com.wms.master.domain.event;

import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.UUID;

public record LotCreatedEvent(
        UUID aggregateId,
        Lot snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LotCreatedEvent from(Lot lot) {
        return new LotCreatedEvent(
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
        return "master.lot.created";
    }
}
