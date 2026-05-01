package com.wms.outbound.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code shipment}.
 *
 * <p>V4 created core columns; V11 added {@code shipment_no},
 * {@code tms_status}, {@code tms_notified_at}, {@code tms_request_id},
 * {@code version}, {@code updated_at}.
 */
@Entity
@Table(name = "shipment")
public class ShipmentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "shipment_no", length = 40)
    private String shipmentNo;

    @Column(name = "carrier", length = 100)
    private String carrierCode;

    @Column(name = "tracking_number", length = 200)
    private String trackingNo;

    /** Bootstrap-era status column; v1 mirrors {@code tms_status} (legacy default 'PENDING'). */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "tms_status", nullable = false, length = 30)
    private String tmsStatus;

    @Column(name = "tms_notified_at")
    private Instant tmsNotifiedAt;

    @Column(name = "tms_request_id")
    private UUID tmsRequestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ShipmentEntity() {
    }

    public ShipmentEntity(UUID id, UUID orderId, String shipmentNo, String carrierCode,
                          String trackingNo, Instant shippedAt, String tmsStatus,
                          Instant tmsNotifiedAt, UUID tmsRequestId,
                          Instant createdAt, String createdBy, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.shipmentNo = shipmentNo;
        this.carrierCode = carrierCode;
        this.trackingNo = trackingNo;
        this.status = tmsStatus; // bootstrap mirror
        this.shippedAt = shippedAt;
        this.tmsStatus = tmsStatus;
        this.tmsNotifiedAt = tmsNotifiedAt;
        this.tmsRequestId = tmsRequestId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getShipmentNo() { return shipmentNo; }
    public String getCarrierCode() { return carrierCode; }
    public String getTrackingNo() { return trackingNo; }
    public Instant getShippedAt() { return shippedAt; }
    public String getTmsStatus() { return tmsStatus; }
    public Instant getTmsNotifiedAt() { return tmsNotifiedAt; }
    public UUID getTmsRequestId() { return tmsRequestId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public void setTmsStatus(String tmsStatus) {
        this.tmsStatus = tmsStatus;
        this.status = tmsStatus;
    }
    public void setTmsNotifiedAt(Instant tmsNotifiedAt) { this.tmsNotifiedAt = tmsNotifiedAt; }
    public void setTmsRequestId(UUID tmsRequestId) { this.tmsRequestId = tmsRequestId; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
