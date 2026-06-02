package com.wms.admin.readmodel.alert;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertLogRepository extends JpaRepository<AlertLogEntity, UUID> {

    // NOTE: the nullable temporal bounds wrap their `IS NULL` guard in
    // `CAST(:p AS string)`. A bare `:detectedAtFrom IS NULL` binds an UNTYPED
    // null to PostgreSQL, which cannot infer the parameter type and aborts the
    // whole statement with `42P18 could not determine data type of parameter`
    // (→ a 500 on every unfiltered /dashboard/alerts call). The cast pins the
    // parameter to a concrete type for the null-check while the real
    // comparison (`a.detectedAt >= :detectedAtFrom`) keeps its temporal typing;
    // a cast-to-string preserves IS-NULL semantics for any value. The UUID /
    // String / Boolean bounds do NOT need it (PostgreSQL resolves their
    // untyped nulls). TASK-BE-331.
    @Query("SELECT a FROM AlertLogEntity a "
            + "WHERE (:alertType IS NULL OR a.alertType = :alertType) "
            + "AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:acknowledged IS NULL "
            + "    OR (:acknowledged = TRUE  AND a.acknowledgedAt IS NOT NULL) "
            + "    OR (:acknowledged = FALSE AND a.acknowledgedAt IS NULL)) "
            + "AND (CAST(:detectedAtFrom AS string) IS NULL OR a.detectedAt >= :detectedAtFrom) "
            + "AND (CAST(:detectedAtTo AS string) IS NULL OR a.detectedAt <= :detectedAtTo)")
    Page<AlertLogEntity> search(@Param("alertType") String alertType,
                                @Param("warehouseId") UUID warehouseId,
                                @Param("acknowledged") Boolean acknowledged,
                                @Param("detectedAtFrom") Instant detectedAtFrom,
                                @Param("detectedAtTo") Instant detectedAtTo,
                                Pageable pageable);
}
