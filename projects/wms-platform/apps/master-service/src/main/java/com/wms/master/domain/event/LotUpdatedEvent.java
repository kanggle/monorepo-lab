package com.wms.master.domain.event;

import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LotUpdatedEvent(
        UUID aggregateId,
        Lot snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LotUpdatedEvent from(Lot lot, List<String> changedFields) {
        return new LotUpdatedEvent(
                lot.getId(),
                lot,
                List.copyOf(changedFields),
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "lot";
    }

    @Override
    public String eventType() {
        return "master.lot.updated";
    }
}
