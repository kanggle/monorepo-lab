package com.wms.master.domain.event;

import com.wms.master.domain.model.Warehouse;
import java.time.Instant;
import java.util.UUID;

public record WarehouseDeactivatedEvent(
        UUID aggregateId,
        Warehouse snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static WarehouseDeactivatedEvent from(Warehouse warehouse, String reason) {
        return new WarehouseDeactivatedEvent(
                warehouse.getId(),
                warehouse,
                reason,
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "warehouse";
    }

    @Override
    public String eventType() {
        return "master.warehouse.deactivated";
    }
}
