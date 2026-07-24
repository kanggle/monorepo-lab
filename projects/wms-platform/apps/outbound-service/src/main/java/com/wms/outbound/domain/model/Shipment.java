package com.wms.outbound.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shipment aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §5.
 *
 * <p>Effectively immutable after creation. Carrier dispatch (the former TMS
 * side-channel) was relocated to the scm {@code logistics-service}
 * (ADR-MONO-053 §D8), so the shipment no longer tracks a TMS notification
 * status; it records that the order shipped and its carrier/tracking metadata.
 */
public final class Shipment {

    private final UUID id;
    private final UUID orderId;
    private final String shipmentNo;
    private String carrierCode;
    private String trackingNo;
    private final Instant shippedAt;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    public Shipment(UUID id,
                    UUID orderId,
                    String shipmentNo,
                    String carrierCode,
                    String trackingNo,
                    Instant shippedAt,
                    long version,
                    Instant createdAt,
                    String createdBy,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.shipmentNo = Objects.requireNonNull(shipmentNo, "shipmentNo");
        this.carrierCode = carrierCode;
        this.trackingNo = trackingNo;
        this.shippedAt = Objects.requireNonNull(shippedAt, "shippedAt");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getShipmentNo() {
        return shipmentNo;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
