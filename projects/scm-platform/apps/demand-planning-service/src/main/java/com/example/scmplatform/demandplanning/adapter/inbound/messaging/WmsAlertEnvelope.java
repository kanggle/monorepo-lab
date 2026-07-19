package com.example.scmplatform.demandplanning.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Deserialization DTO for the wms-platform event envelope (camelCase convention).
 * Consumed subset per replenishment-subscriptions.md.
 *
 * <p>wms events use camelCase envelope fields — NOT the scm BaseEventPublisher shape.
 * Authoritative shape: wms inventory-events.md § Global Envelope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WmsAlertEnvelope(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("eventVersion") int eventVersion,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("payload") Map<String, Object> payload
) {
    /**
     * Validate required fields for idempotency and routing.
     * null eventId or null payload → non-retryable DLT.
     */
    public boolean isValid() {
        return eventId != null
                && eventType != null && !eventType.isBlank()
                && occurredAt != null
                && payload != null;
    }

    /**
     * Helper to extract the skuCode from the payload (join key).
     */
    public String skuCode() {
        if (payload == null) return null;
        Object v = payload.get("skuCode");
        return v instanceof String s ? s : null;
    }

    /**
     * Extract warehouseId — use locationId as the warehouse dimension key (dedup key).
     */
    public String locationId() {
        if (payload == null) return null;
        Object v = payload.get("locationId");
        return v instanceof String s ? s : null;
    }

    /**
     * Extract the warehouse business CODE (ADR-MONO-050 D9, additive on the alert).
     * This is the value that flows to the PO {@code destinationWarehouseId} so wms's
     * {@code findWarehouseByCode} resolves it — distinct from {@link #locationId()}
     * (the internal UUID kept only as the dedup-key dimension). May be null on an
     * older alert that predates the additive field.
     */
    public String warehouseCode() {
        if (payload == null) return null;
        Object v = payload.get("warehouseCode");
        return v instanceof String s ? s : null;
    }

    /**
     * Extract available quantity.
     */
    public int availableQty() {
        if (payload == null) return 0;
        Object v = payload.get("availableQty");
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * Extract wms alert threshold (informational only — scm owns its reorder_point, D4).
     */
    public int threshold() {
        if (payload == null) return 0;
        Object v = payload.get("threshold");
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
