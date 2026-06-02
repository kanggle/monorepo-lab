package com.wms.admin.readmodel.outbound;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSummaryRepository extends JpaRepository<OrderSummaryEntity, UUID> {

    // CAST(:p AS string) on the nullable temporal IS-NULL guards — PostgreSQL
    // 42P18 fix (untyped null on an unfiltered call → 500). Same as
    // AlertLogRepository (TASK-BE-331); the >=/<= keeps temporal typing. TASK-BE-332.
    @Query("SELECT o FROM OrderSummaryEntity o "
            + "WHERE (:warehouseId IS NULL OR o.warehouseId = :warehouseId) "
            + "AND (:customerPartnerId IS NULL OR o.customerPartnerId = :customerPartnerId) "
            + "AND (:status IS NULL OR o.status = :status) "
            + "AND (:sagaState IS NULL OR o.sagaState = :sagaState) "
            + "AND (CAST(:requiredShipDateFrom AS string) IS NULL OR o.requiredShipDate >= :requiredShipDateFrom) "
            + "AND (CAST(:requiredShipDateTo AS string) IS NULL OR o.requiredShipDate <= :requiredShipDateTo)")
    Page<OrderSummaryEntity> search(@Param("warehouseId") UUID warehouseId,
                                    @Param("customerPartnerId") UUID customerPartnerId,
                                    @Param("status") String status,
                                    @Param("sagaState") String sagaState,
                                    @Param("requiredShipDateFrom") LocalDate requiredShipDateFrom,
                                    @Param("requiredShipDateTo") LocalDate requiredShipDateTo,
                                    Pageable pageable);
}
