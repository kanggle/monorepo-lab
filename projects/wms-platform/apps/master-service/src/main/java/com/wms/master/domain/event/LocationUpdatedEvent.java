package com.wms.master.domain.event;

import com.wms.master.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LocationUpdatedEvent(
        UUID aggregateId,
        Location snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static LocationUpdatedEvent from(Location location, List<String> changedFields) {
        return new LocationUpdatedEvent(
                location.getId(),
                location,
                List.copyOf(changedFields),
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "location";
    }

    @Override
    public String eventType() {
        return "master.location.updated";
    }
}
