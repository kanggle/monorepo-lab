package com.wms.admin.readmodel.inventory;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdjustmentAuditRepository extends JpaRepository<AdjustmentAuditEntity, UUID> {

    @Query("SELECT a FROM AdjustmentAuditEntity a "
            + "WHERE (:warehouseId IS NULL OR a.warehouseId = :warehouseId) "
            + "AND (:locationId IS NULL OR a.locationId = :locationId) "
            + "AND (:skuId IS NULL OR a.skuId = :skuId) "
            + "AND (:bucket IS NULL OR a.bucket = :bucket) "
            + "AND (:reasonCode IS NULL OR a.reasonCode = :reasonCode)")
    Page<AdjustmentAuditEntity> search(@Param("warehouseId") UUID warehouseId,
                                       @Param("locationId") UUID locationId,
                                       @Param("skuId") UUID skuId,
                                       @Param("bucket") String bucket,
                                       @Param("reasonCode") String reasonCode,
                                       Pageable pageable);
}
