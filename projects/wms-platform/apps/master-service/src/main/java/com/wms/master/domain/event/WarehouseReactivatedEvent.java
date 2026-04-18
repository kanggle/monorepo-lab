package com.wms.master.domain.event;

import com.wms.master.domain.model.Warehouse;
import java.time.Instant;
import java.util.UUID;

public record WarehouseReactivatedEvent(
        UUID aggregateId,
        Warehouse snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static WarehouseReactivatedEvent from(Warehouse warehouse) {
        return new WarehouseReactivatedEvent(
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
        return "master.warehouse.reactivated";
    }
}
