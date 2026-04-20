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
        LocationReactivatedEvent,
        SkuCreatedEvent,
        SkuUpdatedEvent,
        SkuDeactivatedEvent,
        SkuReactivatedEvent,
        LotCreatedEvent,
        LotUpdatedEvent,
        LotDeactivatedEvent,
        LotReactivatedEvent,
        LotExpiredEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    Instant occurredAt();

    /**
     * JWT subject that originated the change, or {@code null} for
     * system-originated events (e.g. {@link LotExpiredEvent}, where the
     * scheduled expiration job has no user context).
     */
    String actorId();
}
