package com.wms.admin.readmodel.outbound;

import java.time.Instant;
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
    @Query("SELECT s FROM ShipmentSummaryEntity s "
            + "WHERE (:warehouseId IS NULL OR s.warehouseId = :warehouseId) "
            + "AND (:orderId IS NULL OR s.orderId = :orderId) "
            + "AND (:carrierCode IS NULL OR s.carrierCode = :carrierCode) "
            + "AND (CAST(:shippedAtFrom AS string) IS NULL OR s.shippedAt >= :shippedAtFrom) "
            + "AND (CAST(:shippedAtTo AS string) IS NULL OR s.shippedAt <= :shippedAtTo)")
    Page<ShipmentSummaryEntity> search(@Param("warehouseId") UUID warehouseId,
                                       @Param("orderId") UUID orderId,
                                       @Param("carrierCode") String carrierCode,
                                       @Param("shippedAtFrom") Instant shippedAtFrom,
                                       @Param("shippedAtTo") Instant shippedAtTo,
                                       Pageable pageable);
}
