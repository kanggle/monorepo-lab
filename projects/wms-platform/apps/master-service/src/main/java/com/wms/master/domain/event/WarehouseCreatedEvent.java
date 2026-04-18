package com.wms.master.domain.event;

import com.wms.master.domain.model.Warehouse;
import java.time.Instant;
import java.util.UUID;

public record WarehouseCreatedEvent(
        UUID aggregateId,
        Warehouse snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static WarehouseCreatedEvent from(Warehouse warehouse) {
        return new WarehouseCreatedEvent(
                warehouse.getId(),
                warehouse,
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "warehouse";
    }

    @Override
    public String eventType() {
        return "master.warehouse.created";
    }
}
