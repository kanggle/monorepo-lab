package com.wms.master.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent
    permits
        WarehouseCreatedEvent,
        WarehouseUpdatedEvent,
        WarehouseDeactivatedEvent,
        WarehouseReactivatedEvent,
        ZoneCreatedEvent,
        ZoneUpdatedEvent,
        ZoneDeactivatedEvent,
        ZoneReactivatedEvent,
        LocationCreatedEvent,
        LocationUpdatedEvent,
        LocationDeactivatedEvent,
        LocationReactivatedEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    Instant occurredAt();

    String actorId();
}
