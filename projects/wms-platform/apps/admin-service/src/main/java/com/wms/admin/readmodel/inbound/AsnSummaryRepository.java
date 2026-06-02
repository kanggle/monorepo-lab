package com.wms.admin.readmodel.inbound;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsnSummaryRepository extends JpaRepository<AsnSummaryEntity, UUID> {

    // CAST(:p AS string) on the nullable temporal IS-NULL guards — PostgreSQL
    // 42P18 fix (untyped null on an unfiltered call → 500). Same as
    // AlertLogRepository (TASK-BE-331); the >=/<= keeps temporal typing. TASK-BE-332.
    @Query("SELECT a FROM AsnSummaryEntity a "
            + "WHERE (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:supplierPartnerId IS NULL OR a.supplierPartnerId = :supplierPartnerId) "
            + "AND (:status IS NULL OR a.status = :status) "
            + "AND (:source IS NULL OR a.source = :source) "
            + "AND (CAST(:expectedArriveDateFrom AS string) IS NULL OR a.expectedArriveDate >= :expectedArriveDateFrom) "
            + "AND (CAST(:expectedArriveDateTo AS string) IS NULL OR a.expectedArriveDate <= :expectedArriveDateTo)")
    Page<AsnSummaryEntity> search(@Param("warehouseId") UUID warehouseId,
                                  @Param("supplierPartnerId") UUID supplierPartnerId,
                                  @Param("status") String status,
                                  @Param("source") String source,
                                  @Param("expectedArriveDateFrom") LocalDate expectedArriveDateFrom,
                                  @Param("expectedArriveDateTo") LocalDate expectedArriveDateTo,
                                  Pageable pageable);
}
