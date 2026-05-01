package com.wms.outbound.domain.model;

import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shipment aggregate (pure POJO — no JPA annotations).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §5.
 *
 * <p>Mostly immutable: only {@code tms_status} / {@code tms_notified_at} /
 * {@code tracking_no} / {@code carrier_code} may change after creation, and
 * only via the {@link #markTmsNotified} / {@link #markTmsNotifyFailed}
 * domain methods (T4).
 */
public final class Shipment {

    private final UUID id;
    private final UUID orderId;
    private final String shipmentNo;
    private String carrierCode;
    private String trackingNo;
    private final Instant shippedAt;
    private TmsStatus tmsStatus;
    private Instant tmsNotifiedAt;
    private UUID tmsRequestId;
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
                    TmsStatus tmsStatus,
                    Instant tmsNotifiedAt,
                    UUID tmsRequestId,
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
        this.tmsStatus = Objects.requireNonNull(tmsStatus, "tmsStatus");
        this.tmsNotifiedAt = tmsNotifiedAt;
        this.tmsRequestId = tmsRequestId;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Records a successful TMS acknowledgement: {@code PENDING} or
     * {@code NOTIFY_FAILED} → {@code NOTIFIED}.
     *
     * <p>Manual retry (`POST /shipments/{id}:retry-tms-notify`) may invoke this
     * from {@code NOTIFY_FAILED}; first-time success invokes from {@code PENDING}.
     */
    public void markTmsNotified(Instant now, UUID requestId) {
        if (tmsStatus == TmsStatus.NOTIFIED) {
            throw new StateTransitionInvalidException(
                    TmsStatus.NOTIFIED.name(), TmsStatus.NOTIFIED.name());
        }
        this.tmsStatus = TmsStatus.NOTIFIED;
        this.tmsNotifiedAt = now;
        this.tmsRequestId = requestId;
        this.updatedAt = now;
    }

    /**
     * Records a TMS push exhaustion: {@code PENDING} → {@code NOTIFY_FAILED}.
     */
    public void markTmsNotifyFailed(Instant now) {
        if (tmsStatus == TmsStatus.NOTIFY_FAILED) {
            return;
        }
        if (tmsStatus != TmsStatus.PENDING) {
            throw new StateTransitionInvalidException(
                    tmsStatus.name(), TmsStatus.NOTIFY_FAILED.name());
        }
        this.tmsStatus = TmsStatus.NOTIFY_FAILED;
        this.updatedAt = now;
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

    public TmsStatus getTmsStatus() {
        return tmsStatus;
    }

    public Instant getTmsNotifiedAt() {
        return tmsNotifiedAt;
    }

    public UUID getTmsRequestId() {
        return tmsRequestId;
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
