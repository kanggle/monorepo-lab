package com.wms.master.domain.event;

import com.wms.master.domain.model.Zone;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ZoneUpdatedEvent(
        UUID aggregateId,
        Zone snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static ZoneUpdatedEvent from(Zone zone, List<String> changedFields) {
        return new ZoneUpdatedEvent(
                zone.getId(),
                zone,
                List.copyOf(changedFields),
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "zone";
    }

    @Override
    public String eventType() {
        return "master.zone.updated";
    }
}
