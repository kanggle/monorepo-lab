package com.wms.master.domain.event;

import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.UUID;

public record LotDeactivatedEvent(
        UUID aggregateId,
        Lot snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LotDeactivatedEvent from(Lot lot, String reason) {
        return new LotDeactivatedEvent(
                lot.getId(),
                lot,
                reason,
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "lot";
    }

    @Override
    public String eventType() {
        return "master.lot.deactivated";
    }
}
