package com.wms.master.domain.event;

import com.wms.master.domain.model.Location;
import java.time.Instant;
import java.util.UUID;

public record LocationDeactivatedEvent(
        UUID aggregateId,
        Location snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LocationDeactivatedEvent from(Location location, String reason) {
        return new LocationDeactivatedEvent(
                location.getId(),
                location,
                reason,
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "location";
    }

    @Override
    public String eventType() {
        return "master.location.deactivated";
    }
}
