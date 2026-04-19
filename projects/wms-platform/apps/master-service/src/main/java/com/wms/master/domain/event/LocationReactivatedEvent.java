package com.wms.master.domain.event;

import com.wms.master.domain.model.Location;
import java.time.Instant;
import java.util.UUID;

public record LocationReactivatedEvent(
        UUID aggregateId,
        Location snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LocationReactivatedEvent from(Location location) {
        return new LocationReactivatedEvent(
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
        return "master.location.reactivated";
    }
}
