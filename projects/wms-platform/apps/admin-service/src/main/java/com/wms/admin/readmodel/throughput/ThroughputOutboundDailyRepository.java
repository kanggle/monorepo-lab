package com.wms.admin.readmodel.throughput;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThroughputOutboundDailyRepository
        extends JpaRepository<ThroughputOutboundDailyEntity, ThroughputDailyId> {

    List<ThroughputOutboundDailyEntity> findByWarehouseIdAndDateBetweenOrderByDateAsc(
            UUID warehouseId, LocalDate from, LocalDate to);

    /**
     * Atomic increment for {@code admin_throughput_outbound_daily}. Every call
     * inserts or increments — it always affects exactly one row.
     *
     * <p>🔴 No {@code last_event_at} guard (TASK-BE-643, {@code idempotency.md § 2.6}).
     * Same reasoning as {@link ThroughputInboundDailyRepository#upsertIncrement}:
     * shipments of different orders (partitioned by {@code orderId}) have no
     * cross-order ordering guarantee, and the old
     * {@code WHERE last_event_at < EXCLUDED.last_event_at} dropped late arrivals.
     * Worse than inbound, the caller ignored the 0-row result and recorded
     * {@code APPLIED}, so the loss left no trace. Dedupe is the only idempotency
     * mechanism; {@code last_event_at} is a bookkeeping maximum.
     *
     * <p>Replaces the read-modify-write pattern in
     * {@code OutboundProjectionService.onShippingConfirmed} (TASK-BE-048 #5).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO admin_throughput_outbound_daily "
            + "(date, warehouse_id, shipment_count, qty_shipped, last_event_at, version) "
            + "VALUES (:date, :warehouseId, 1, :qtyDelta, :lastEventAt, 1) "
            + "ON CONFLICT (date, warehouse_id) DO UPDATE "
            + "SET shipment_count = admin_throughput_outbound_daily.shipment_count + 1, "
            + "    qty_shipped = admin_throughput_outbound_daily.qty_shipped + EXCLUDED.qty_shipped, "
            + "    last_event_at = GREATEST(admin_throughput_outbound_daily.last_event_at, "
            + "                             EXCLUDED.last_event_at), "
            + "    version = admin_throughput_outbound_daily.version + 1",
            nativeQuery = true)
    int upsertIncrement(@Param("date") LocalDate date,
                        @Param("warehouseId") UUID warehouseId,
                        @Param("qtyDelta") int qtyDelta,
                        @Param("lastEventAt") Instant lastEventAt);
}
