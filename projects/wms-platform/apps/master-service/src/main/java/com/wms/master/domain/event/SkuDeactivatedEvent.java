package com.wms.master.domain.event;

import com.wms.master.domain.model.Sku;
import java.time.Instant;
import java.util.UUID;

public record SkuDeactivatedEvent(
        UUID aggregateId,
        Sku snapshot,
        String reason,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static SkuDeactivatedEvent from(Sku sku, String reason) {
        return new SkuDeactivatedEvent(
                sku.getId(),
                sku,
                reason,
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "sku";
    }

    @Override
    public String eventType() {
        return "master.sku.deactivated";
    }
}
