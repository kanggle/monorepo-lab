package com.example.product.domain.event;

import com.example.product.domain.tenant.TenantContext;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ProductEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        EventPayload payload
) {
    public static ProductEvent created(ProductCreatedPayload payload) {
        return of("ProductCreated", payload);
    }

    public static ProductEvent updated(ProductUpdatedPayload payload) {
        return of("ProductUpdated", payload);
    }

    public static ProductEvent deleted(ProductDeletedPayload payload) {
        return of("ProductDeleted", payload);
    }

    public static ProductEvent stockChanged(StockChangedPayload payload) {
        return of("StockChanged", payload);
    }

    public static ProductEvent imagesUpdated(ProductImagesUpdatedPayload payload) {
        return of("ProductImagesUpdated", payload);
    }

    public static ProductEvent orderReservationFailed(OrderReservationFailedPayload payload) {
        return of("OrderReservationFailed", payload);
    }

    private static ProductEvent of(String eventType, EventPayload payload) {
        // Tenant context propagation across the async boundary (M5): the envelope
        // carries the tenant owning the product. Background/reconciliation threads
        // (no request context) resolve to the default tenant (net-zero, D8).
        return new ProductEvent(UUID.randomUUID(), eventType, Instant.now(), "product-service",
                TenantContext.currentTenant(), payload);
    }
}
