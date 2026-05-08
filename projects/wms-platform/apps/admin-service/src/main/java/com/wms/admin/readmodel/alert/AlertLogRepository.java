package com.wms.admin.readmodel.alert;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertLogRepository extends JpaRepository<AlertLogEntity, UUID> {

    @Query("SELECT a FROM AlertLogEntity a "
            + "WHERE (:alertType IS NULL OR a.alertType = :alertType) "
            + "AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:acknowledged IS NULL "
            + "    OR (:acknowledged = TRUE  AND a.acknowledgedAt IS NOT NULL) "
            + "    OR (:acknowledged = FALSE AND a.acknowledgedAt IS NULL)) "
            + "AND (:detectedAtFrom IS NULL OR a.detectedAt >= :detectedAtFrom) "
            + "AND (:detectedAtTo IS NULL OR a.detectedAt <= :detectedAtTo)")
    Page<AlertLogEntity> search(@Param("alertType") String alertType,
                                @Param("warehouseId") UUID warehouseId,
                                @Param("acknowledged") Boolean acknowledged,
                                @Param("detectedAtFrom") Instant detectedAtFrom,
                                @Param("detectedAtTo") Instant detectedAtTo,
                                Pageable pageable);
}
