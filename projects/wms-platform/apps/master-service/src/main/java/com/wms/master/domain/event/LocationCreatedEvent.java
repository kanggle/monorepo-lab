package com.wms.master.domain.event;

import com.wms.master.domain.model.Location;
import java.time.Instant;
import java.util.UUID;

public record LocationCreatedEvent(
        UUID aggregateId,
        Location snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LocationCreatedEvent from(Location location) {
        return new LocationCreatedEvent(
                location.getId(),
                location,
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "location";
    }

    @Override
    public String eventType() {
        return "master.location.created";
    }
}
