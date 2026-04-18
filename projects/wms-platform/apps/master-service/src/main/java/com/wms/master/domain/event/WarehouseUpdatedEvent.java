package com.wms.master.domain.event;

import com.wms.master.domain.model.Warehouse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WarehouseUpdatedEvent(
        UUID aggregateId,
        Warehouse snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static WarehouseUpdatedEvent from(Warehouse warehouse, List<String> changedFields) {
        return new WarehouseUpdatedEvent(
                warehouse.getId(),
                warehouse,
                List.copyOf(changedFields),
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "warehouse";
    }

    @Override
    public String eventType() {
        return "master.warehouse.updated";
    }
}
