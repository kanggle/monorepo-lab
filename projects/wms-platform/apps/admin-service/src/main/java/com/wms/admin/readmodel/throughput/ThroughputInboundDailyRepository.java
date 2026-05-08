package com.wms.admin.readmodel.throughput;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ThroughputInboundDailyRepository
        extends JpaRepository<ThroughputInboundDailyEntity, ThroughputDailyId> {

    List<ThroughputInboundDailyEntity> findByWarehouseIdAndDateBetweenOrderByDateAsc(
            UUID warehouseId, LocalDate from, LocalDate to);

    /**
     * Atomic LWW-guarded increment for {@code admin_throughput_inbound_daily}.
     * Returns the affected row count: {@code 1} when the row was inserted or
     * updated, {@code 0} when an existing row already holds a newer
     * {@code last_event_at} (stale event silently skipped).
     *
     * <p>Replaces the read-modify-write pattern in
     * {@code InboundProjectionService.onPutawayCompleted} (TASK-BE-048 #5).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO admin_throughput_inbound_daily "
            + "(date, warehouse_id, putaway_count, qty_received, last_event_at, version) "
            + "VALUES (:date, :warehouseId, 1, :qtyDelta, :lastEventAt, 1) "
            + "ON CONFLICT (date, warehouse_id) DO UPDATE "
            + "SET putaway_count = admin_throughput_inbound_daily.putaway_count + 1, "
            + "    qty_received = admin_throughput_inbound_daily.qty_received + EXCLUDED.qty_received, "
            + "    last_event_at = EXCLUDED.last_event_at, "
            + "    version = admin_throughput_inbound_daily.version + 1 "
            + "WHERE admin_throughput_inbound_daily.last_event_at < EXCLUDED.last_event_at",
            nativeQuery = true)
    int upsertIncrement(@Param("date") LocalDate date,
                        @Param("warehouseId") UUID warehouseId,
                        @Param("qtyDelta") int qtyDelta,
                        @Param("lastEventAt") Instant lastEventAt);
}
