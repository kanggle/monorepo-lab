package com.wms.master.domain.event;

import com.wms.master.domain.model.Sku;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SkuUpdatedEvent(
        UUID aggregateId,
        Sku snapshot,
        List<String> changedFields,
        Instant occurredAt,
        String actorId) implements DomainEvent {

    public static SkuUpdatedEvent from(Sku sku, List<String> changedFields) {
        return new SkuUpdatedEvent(
                sku.getId(),
                sku,
                List.copyOf(changedFields),
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }

    @Override
    public String aggregateType() {
        return "sku";
    }

    @Override
    public String eventType() {
        return "master.sku.updated";
    }
}
