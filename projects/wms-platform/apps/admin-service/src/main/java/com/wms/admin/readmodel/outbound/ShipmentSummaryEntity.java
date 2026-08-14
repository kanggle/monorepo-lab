package com.wms.admin.readmodel.outbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Projected from {@code wms.outbound.shipping.confirmed.v1}. Per
 * {@code domain-model.md § 9}.
 *
 * <p>{@code tenantId} is the isolation axis of this projection (ADR-MONO-065 § D1),
 * taken from the source event's <b>envelope</b> — the shipment inherits its order's
 * tenant, but it is stored as this row's own column rather than joined at read time
 * because the order and shipment projections arrive independently. A shipment whose
 * order row has not been projected yet therefore still filters correctly.
 */
@Entity
@Table(name = "admin_shipment_summary")
public class ShipmentSummaryEntity {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_no", length = 80)
    private String orderNo;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "shipment_no", length = 80)
    private String shipmentNo;

    @Column(name = "carrier_code", length = 40)
    private String carrierCode;

    @Column(name = "tracking_no", length = 120)
    private String trackingNo;

    @Column(name = "shipped_at", nullable = false)
    private Instant shippedAt;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ShipmentSummaryEntity() {
    }

    /** {@code tenantId} last, after an {@code Instant}, so a stale call site fails to
     *  compile instead of silently binding the tenant to an adjacent String field. */
    public ShipmentSummaryEntity(UUID shipmentId, UUID orderId, String orderNo, UUID warehouseId,
                                 String shipmentNo, String carrierCode, String trackingNo,
                                 Instant shippedAt, int totalQty, Instant lastEventAt,
                                 String tenantId) {
        this.tenantId = tenantId;
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.warehouseId = warehouseId;
        this.shipmentNo = shipmentNo;
        this.carrierCode = carrierCode;
        this.trackingNo = trackingNo;
        this.shippedAt = shippedAt;
        this.totalQty = totalQty;
        this.lastEventAt = lastEventAt;
    }

    public void apply(UUID orderId, String orderNo, UUID warehouseId, String shipmentNo,
                      String carrierCode, String trackingNo, Instant shippedAt, int totalQty,
                      Instant lastEventAt, String tenantId) {
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.warehouseId = warehouseId;
        this.shipmentNo = shipmentNo;
        this.carrierCode = carrierCode;
        this.trackingNo = trackingNo;
        this.shippedAt = shippedAt;
        this.totalQty = totalQty;
        this.lastEventAt = lastEventAt;
    }

    public UUID getShipmentId() { return shipmentId; }
    public String getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public String getOrderNo() { return orderNo; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getShipmentNo() { return shipmentNo; }
    public String getCarrierCode() { return carrierCode; }
    public String getTrackingNo() { return trackingNo; }
    public Instant getShippedAt() { return shippedAt; }
    public int getTotalQty() { return totalQty; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
