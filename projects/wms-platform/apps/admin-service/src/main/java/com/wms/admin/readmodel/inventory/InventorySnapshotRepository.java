package com.wms.admin.readmodel.inventory;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventorySnapshotRepository
        extends JpaRepository<InventorySnapshotEntity, InventorySnapshotId> {

    /**
     * {@code lowStockOnly} compares against the caller-supplied
     * {@code lowStockThreshold} (the current setting) with strict {@code <} —
     * TASK-BE-644. There is no stored flag: deriving here means a setting
     * change applies to every row at once.
     */
    @Query("SELECT i FROM InventorySnapshotEntity i "
            + "WHERE (:warehouseId IS NULL OR i.warehouseId = :warehouseId) "
            + "AND (:locationId IS NULL OR i.locationId = :locationId) "
            + "AND (:skuId IS NULL OR i.skuId = :skuId) "
            + "AND (:lotId IS NULL OR i.lotId = :lotId) "
            + "AND (:lowStockOnly = FALSE OR i.availableQty < :lowStockThreshold) "
            + "AND (:minOnHand IS NULL OR i.onHandQty >= :minOnHand)")
    Page<InventorySnapshotEntity> search(@Param("warehouseId") UUID warehouseId,
                                         @Param("locationId") UUID locationId,
                                         @Param("skuId") UUID skuId,
                                         @Param("lotId") UUID lotId,
                                         @Param("lowStockOnly") boolean lowStockOnly,
                                         @Param("lowStockThreshold") int lowStockThreshold,
                                         @Param("minOnHand") Integer minOnHand,
                                         Pageable pageable);
}
