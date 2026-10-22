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
     * Atomic increment for {@code admin_throughput_inbound_daily}. Every call
     * inserts or increments — it always affects exactly one row.
     *
     * <p>🔴 No {@code last_event_at} guard (TASK-BE-643, {@code idempotency.md § 2.6}).
     * Until 2026-10-22 the {@code DO UPDATE} carried
     * {@code WHERE last_event_at < EXCLUDED.last_event_at}. A row here is
     * «warehouse × day» and its events come from different ASNs, partitioned by
     * {@code asnId} — so an earlier-stamped putaway arriving late is another
     * putaway, not a stale version, and the guard silently dropped it (and the
     * second of two equal {@code occurredAt}s). Idempotency is
     * {@code admin_event_dedupe}'s job alone; {@code last_event_at} is kept as a
     * maximum for bookkeeping. Do not reintroduce the guard.
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
            + "    last_event_at = GREATEST(admin_throughput_inbound_daily.last_event_at, "
            + "                             EXCLUDED.last_event_at), "
            + "    version = admin_throughput_inbound_daily.version + 1",
            nativeQuery = true)
    int upsertIncrement(@Param("date") LocalDate date,
                        @Param("warehouseId") UUID warehouseId,
                        @Param("qtyDelta") int qtyDelta,
                        @Param("lastEventAt") Instant lastEventAt);
}
