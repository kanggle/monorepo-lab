package com.example.order.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound event record mirroring product-service StockChanged contract.
 * See specs/contracts/events/product-events.md
 */
public record StockChangedEvent(
        @JsonProperty("event_id") @JsonAlias("eventId") String eventId,
        @JsonProperty("event_type") @JsonAlias("eventType") String eventType,
        @JsonProperty("occurred_at") @JsonAlias("occurredAt") String occurredAt,
        String source,
        // Envelope tenant (ADR-MONO-030 Step 2, M5). product-service stamps this on
        // the StockChanged envelope (increment B); the consumer binds it so the
        // product→order confirm saga stays within the tenant boundary. Absent on a
        // pre-multi-tenant / standalone event → resolves to the default tenant.
        @JsonProperty("tenant_id") @JsonAlias("tenantId") String tenantId,
        StockChangedPayload payload
) {
    public record StockChangedPayload(
            String productId,
            String variantId,
            int previousStock,
            int currentStock,
            int delta,
            String reason,
            String orderId
    ) {}
}
