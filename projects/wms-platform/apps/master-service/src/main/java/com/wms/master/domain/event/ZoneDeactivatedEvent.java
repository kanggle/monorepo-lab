package com.wms.master.domain.event;

import com.wms.master.domain.model.Zone;
import java.time.Instant;
import java.util.UUID;

public record ZoneDeactivatedEvent(
        UUID aggregateId,
        Zone snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static ZoneDeactivatedEvent from(Zone zone, String reason) {
        return new ZoneDeactivatedEvent(
                zone.getId(),
                zone,
                reason,
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "zone";
    }

    @Override
    public String eventType() {
        return "master.zone.deactivated";
    }
}
