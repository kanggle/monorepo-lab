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
    //
    // :tenantId is the ISOLATION filter (ADR-MONO-065 D1), not a client-facing one —
    // the controller binds it from ReadScope.tenantFilter(), never from a request
    // parameter. null means an unrestricted caller and matches every row, INCLUDING
    // rows whose own tenantId is null; that is why the guard is ":tenantId IS NULL OR"
    // rather than a null-tolerant equality. It needs no CAST: a String parameter is
    // typed by its own comparison, as :status and :sagaState already are here.
    @Query("SELECT o FROM OrderSummaryEntity o "
            + "WHERE (:tenantId IS NULL OR o.tenantId = :tenantId) "
            + "AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId) "
            + "AND (:customerPartnerId IS NULL OR o.customerPartnerId = :customerPartnerId) "
            + "AND (:status IS NULL OR o.status = :status) "
            + "AND (:sagaState IS NULL OR o.sagaState = :sagaState) "
            + "AND (CAST(:requiredShipDateFrom AS string) IS NULL OR o.requiredShipDate >= :requiredShipDateFrom) "
            + "AND (CAST(:requiredShipDateTo AS string) IS NULL OR o.requiredShipDate <= :requiredShipDateTo)")
    Page<OrderSummaryEntity> search(@Param("tenantId") String tenantId,
                                    @Param("warehouseId") UUID warehouseId,
                                    @Param("customerPartnerId") UUID customerPartnerId,
                                    @Param("status") String status,
                                    @Param("sagaState") String sagaState,
                                    @Param("requiredShipDateFrom") LocalDate requiredShipDateFrom,
                                    @Param("requiredShipDateTo") LocalDate requiredShipDateTo,
                                    Pageable pageable);
}
