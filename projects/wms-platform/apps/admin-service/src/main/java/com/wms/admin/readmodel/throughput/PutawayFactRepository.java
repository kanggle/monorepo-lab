package com.wms.admin.readmodel.throughput;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PutawayFactRepository extends JpaRepository<PutawayFactEntity, UUID> {

    /**
     * Records one applied putaway. {@code DO NOTHING} on a repeated event id: the
     * dedupe row already makes the event exactly-once, so a conflict here can only be
     * the runbook backfill having copied the same event from {@code inbound_outbox}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO admin_putaway_fact (event_id, warehouse_id, completed_at, qty_received) "
            + "VALUES (:eventId, :warehouseId, :completedAt, :qtyReceived) "
            + "ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("warehouseId") UUID warehouseId,
                       @Param("completedAt") Instant completedAt,
                       @Param("qtyReceived") int qtyReceived);

    /** Putaways completed in {@code [from, to)}, optionally in one warehouse. */
    @Query("SELECT new com.wms.admin.readmodel.throughput.TimedQuantity(f.completedAt, f.qtyReceived) "
            + "FROM PutawayFactEntity f "
            + "WHERE (:warehouseId IS NULL OR f.warehouseId = :warehouseId) "
            + "AND f.completedAt >= :from AND f.completedAt < :to")
    List<TimedQuantity> findInWindow(@Param("warehouseId") UUID warehouseId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);
}
