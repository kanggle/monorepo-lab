package com.wms.admin.readmodel.outbound;

import com.wms.admin.readmodel.throughput.TimedQuantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentSummaryRepository extends JpaRepository<ShipmentSummaryEntity, UUID> {

    // CAST(:p AS string) on the nullable temporal IS-NULL guards — a bare
    // `:shippedAtFrom IS NULL` binds an untyped null PostgreSQL cannot type
    // (42P18) on an unfiltered call → 500. Same fix as AlertLogRepository
    // (TASK-BE-331); the >=/<= comparison keeps temporal typing. TASK-BE-332.
    // :tenantId is the ISOLATION filter (ADR-MONO-065 D1), bound from
    // ReadScope.tenantFilter() and never from a request parameter. It is ANDed with
    // :orderId, so narrowing by another tenant's order id yields an empty page rather
    // than that tenant's shipment.
    @Query("SELECT s FROM ShipmentSummaryEntity s "
            + "WHERE (:tenantId IS NULL OR s.tenantId = :tenantId) "
            + "AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId) "
            + "AND (:orderId IS NULL OR s.orderId = :orderId) "
            + "AND (:carrierCode IS NULL OR s.carrierCode = :carrierCode) "
            + "AND (CAST(:shippedAtFrom AS string) IS NULL OR s.shippedAt >= :shippedAtFrom) "
            + "AND (CAST(:shippedAtTo AS string) IS NULL OR s.shippedAt <= :shippedAtTo)")
    Page<ShipmentSummaryEntity> search(@Param("tenantId") String tenantId,
                                       @Param("warehouseId") UUID warehouseId,
                                       @Param("orderId") UUID orderId,
                                       @Param("carrierCode") String carrierCode,
                                       @Param("shippedAtFrom") Instant shippedAtFrom,
                                       @Param("shippedAtTo") Instant shippedAtTo,
                                       Pageable pageable);

    /**
     * Shipments shipped in {@code [from, to)}, optionally in one warehouse — the
     * outbound side of the performance metrics (TASK-MONO-808). 🔴 No tenant filter:
     * the metrics are the warehouse-global class like § 1.2 throughput (contract
     * § 1.0), and inbound — the other half of the same strip — has no tenant at all.
     */
    @Query("SELECT new com.wms.admin.readmodel.throughput.TimedQuantity(s.shippedAt, s.totalQty) "
            + "FROM ShipmentSummaryEntity s "
            + "WHERE (:warehouseId IS NULL OR s.warehouseId = :warehouseId) "
            + "AND s.shippedAt >= :from AND s.shippedAt < :to")
    List<TimedQuantity> findShippedInWindow(@Param("warehouseId") UUID warehouseId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);
}
