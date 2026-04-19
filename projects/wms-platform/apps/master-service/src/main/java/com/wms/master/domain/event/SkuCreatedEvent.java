package com.wms.master.domain.event;

import com.wms.master.domain.model.Sku;
import java.time.Instant;
import java.util.UUID;

public record SkuCreatedEvent(
        UUID aggregateId,
        Sku snapshot,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static SkuCreatedEvent from(Sku sku) {
        return new SkuCreatedEvent(
                sku.getId(),
                sku,
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "sku";
    }

    @Override
    public String eventType() {
        return "master.sku.created";
    }
}
