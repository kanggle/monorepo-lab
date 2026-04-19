package com.wms.master.domain.event;

import com.wms.master.domain.model.Zone;
import java.time.Instant;
import java.util.UUID;

public record ZoneCreatedEvent(
        UUID aggregateId,
        Zone snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static ZoneCreatedEvent from(Zone zone) {
        return new ZoneCreatedEvent(
                zone.getId(),
                zone,
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "zone";
    }

    @Override
    public String eventType() {
        return "master.zone.created";
    }
}
