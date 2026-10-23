package com.wms.admin.readmodel.throughput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per applied {@code inbound.putaway.completed} event (V6, TASK-MONO-808).
 * The per-event counterpart of {@link ThroughputInboundDailyEntity}: the daily row
 * holds only a date and totals, so it can neither be re-cut to another day boundary
 * nor answer «the previous period up to the same elapsed time» (ADR-MONO-084 D3).
 * Outbound already has this in {@code admin_shipment_summary}.
 *
 * <p>Written in the same transaction as the daily increment, keyed by the event id —
 * the dedupe row has already made that event exactly-once.
 */
@Entity
@Table(name = "admin_putaway_fact")
public class PutawayFactEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "qty_received", nullable = false)
    private int qtyReceived;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected PutawayFactEntity() {
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public int getQtyReceived() {
        return qtyReceived;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
