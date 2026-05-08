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
 * Daily inbound throughput counter projected from
 * {@code wms.inbound.putaway.completed.v1}. Composite PK
 * {@code (date, warehouse_id)}. Atomic increment via
 * {@code INSERT ... ON CONFLICT DO UPDATE}; LWW guarded by
 * {@code last_event_at}. Per {@code domain-model.md § 13}.
 */
@Entity
@Table(name = "admin_throughput_inbound_daily")
@IdClass(ThroughputDailyId.class)
public class ThroughputInboundDailyEntity {

    @Id
    @Column(nullable = false)
    private LocalDate date;

    @Id
    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "putaway_count", nullable = false)
    private int putawayCount;

    @Column(name = "qty_received", nullable = false)
    private int qtyReceived;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ThroughputInboundDailyEntity() {
    }

    public ThroughputInboundDailyEntity(LocalDate date, UUID warehouseId, int putawayCount,
                                        int qtyReceived, Instant lastEventAt) {
        this.date = date;
        this.warehouseId = warehouseId;
        this.putawayCount = putawayCount;
        this.qtyReceived = qtyReceived;
        this.lastEventAt = lastEventAt;
    }

    public void increment(int qtyDelta, Instant lastEventAt) {
        this.putawayCount = this.putawayCount + 1;
        this.qtyReceived = this.qtyReceived + qtyDelta;
        this.lastEventAt = lastEventAt;
    }

    public LocalDate getDate() { return date; }
    public UUID getWarehouseId() { return warehouseId; }
    public int getPutawayCount() { return putawayCount; }
    public int getQtyReceived() { return qtyReceived; }
    public Instant getLastEventAt() { return lastEventAt; }
    public long getVersion() { return version; }
}
