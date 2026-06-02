package com.wms.admin.readmodel.inventory;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdjustmentAuditRepository extends JpaRepository<AdjustmentAuditEntity, UUID> {

    // NOTE: the nullable temporal bounds wrap their `IS NULL` guard in
    // `CAST(:p AS string)` — same PostgreSQL `42P18 could not determine data
    // type of parameter` fix as AlertLogRepository (TASK-BE-331). A bare
    // `:occurredAtFrom IS NULL` binds an untyped null PostgreSQL cannot type,
    // aborting any unfiltered /dashboard/adjustments call with a 500. The cast
    // pins the type for the null-check; the `>=`/`<=` comparison keeps temporal
    // typing. UUID / String bounds resolve fine and are left untouched.
    @Query("SELECT a FROM AdjustmentAuditEntity a "
            + "WHERE (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:locationId IS NULL OR a.locationId = :locationId) "
            + "AND (:skuId IS NULL OR a.skuId = :skuId) "
            + "AND (:bucket IS NULL OR a.bucket = :bucket) "
            + "AND (:reasonCode IS NULL OR a.reasonCode = :reasonCode) "
            + "AND (CAST(:occurredAtFrom AS string) IS NULL OR a.occurredAt >= :occurredAtFrom) "
            + "AND (CAST(:occurredAtTo AS string) IS NULL OR a.occurredAt <= :occurredAtTo)")
    Page<AdjustmentAuditEntity> search(@Param("warehouseId") UUID warehouseId,
                                       @Param("locationId") UUID locationId,
                                       @Param("skuId") UUID skuId,
                                       @Param("bucket") String bucket,
                                       @Param("reasonCode") String reasonCode,
                                       @Param("occurredAtFrom") Instant occurredAtFrom,
                                       @Param("occurredAtTo") Instant occurredAtTo,
                                       Pageable pageable);
}
