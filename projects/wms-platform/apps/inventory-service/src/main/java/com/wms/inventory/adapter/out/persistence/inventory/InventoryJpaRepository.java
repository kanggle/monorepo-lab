package com.wms.inventory.adapter.out.persistence.inventory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, UUID> {

    @Query("""
            SELECT i FROM InventoryJpaEntity i
             WHERE i.locationId = :locationId
               AND i.skuId = :skuId
               AND ((:lotId IS NULL AND i.lotId IS NULL)
                    OR i.lotId = :lotId)
            """)
    Optional<InventoryJpaEntity> findByKey(@Param("locationId") UUID locationId,
                                           @Param("skuId") UUID skuId,
                                           @Param("lotId") UUID lotId);
}
