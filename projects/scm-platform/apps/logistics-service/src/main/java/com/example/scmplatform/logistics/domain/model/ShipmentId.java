package com.example.scmplatform.logistics.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The wms shipment identity carried by the {@code outbound.shipping.confirmed} seam.
 *
 * <p>This single value is the {@code dispatch.shipment_id} unique key, the
 * {@code Idempotency-Key} toward the vendor, and the
 * {@code dispatch_request_dedupe.request_id} (architecture.md § Idempotency; ADR-052 §2.7).
 * Framework-free value object (Hexagonal — no JPA/Spring here).
 */
public record ShipmentId(UUID value) {

    public ShipmentId {
        Objects.requireNonNull(value, "shipmentId value must not be null");
    }

    public static ShipmentId of(UUID value) {
        return new ShipmentId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
