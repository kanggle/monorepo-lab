package com.wms.admin.readmodel.throughput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily outbound throughput counter projected from
 * {@code wms.outbound.shipping.confirmed.v1}.
 */
@Entity
@Table(name = "admin_throughput_outbound_daily")
@IdClass(ThroughputDailyId.class)
public class ThroughputOutboundDailyEntity {

    @Id
    @Column(nullable = false)
    private LocalDate date;

    @Id
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "shipment_count", nullable = false)
    private int shipmentCount;

    @Column(name = "qty_shipped", nullable = false)
    private int qtyShipped;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ThroughputOutboundDailyEntity() {
    }

    public ThroughputOutboundDailyEntity(LocalDate date, UUID warehouseId, int shipmentCount,
                                         int qtyShipped, Instant lastEventAt) {
        this.date = date;
        this.warehouseId = warehouseId;
        this.shipmentCount = shipmentCount;
        this.qtyShipped = qtyShipped;
        this.lastEventAt = lastEventAt;
    }

    public void increment(int qtyDelta, Instant lastEventAt) {
        this.shipmentCount = this.shipmentCount + 1;
        this.qtyShipped = this.qtyShipped + qtyDelta;
        this.lastEventAt = lastEventAt;
    }

    public LocalDate getDate() { return date; }
    public UUID getWarehouseId() { return warehouseId; }
    public int getShipmentCount() { return shipmentCount; }
    public int getQtyShipped() { return qtyShipped; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
