package com.wms.inventory.adapter.out.persistence.inventory;

import java.util.List;
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

    /**
     * Candidate stock rows for a {@code (warehouseId, skuId, lotId)} natural key
     * with {@code available_qty > 0}, used by {@link
     * com.wms.inventory.adapter.in.messaging.outbound.PickingRequestedConsumer}
     * when the {@code outbound.picking.requested} line carries no {@code
     * locationId} (v1 norm — location is bound later at picking).
     *
     * <p>Ordered {@code available_qty DESC, id ASC} so the consumer's
     * greatest-available-first allocation is deterministic across replays and
     * spans multiple rows only when one row is insufficient.
     */
    @Query("""
            SELECT i FROM InventoryJpaEntity i
             WHERE i.warehouseId = :warehouseId
               AND i.skuId = :skuId
               AND ((:lotId IS NULL AND i.lotId IS NULL)
                    OR i.lotId = :lotId)
               AND i.availableQty > 0
             ORDER BY i.availableQty DESC, i.id ASC
            """)
    List<InventoryJpaEntity> findAvailableByWarehouseSkuLot(@Param("warehouseId") UUID warehouseId,
                                                            @Param("skuId") UUID skuId,
                                                            @Param("lotId") UUID lotId);
}
