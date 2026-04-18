package com.wms.master.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent
    permits
        WarehouseCreatedEvent,
        WarehouseUpdatedEvent,
        WarehouseDeactivatedEvent,
        WarehouseReactivatedEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    Instant occurredAt();

    String actorId();
}
