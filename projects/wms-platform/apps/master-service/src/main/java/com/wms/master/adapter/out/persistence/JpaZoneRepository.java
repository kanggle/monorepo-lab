package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaZoneRepository extends JpaRepository<ZoneJpaEntity, UUID> {

    boolean existsByWarehouseIdAndZoneCode(UUID warehouseId, String zoneCode);

    Optional<ZoneJpaEntity> findByWarehouseIdAndZoneCode(UUID warehouseId, String zoneCode);

    /**
     * Filter by warehouseId + optional status + optional zoneType. Null status /
     * zoneType parameters mean "do not filter" for that column.
     */
    @Query("""
            SELECT z FROM ZoneJpaEntity z
            WHERE z.warehouseId = :warehouseId
              AND (:status IS NULL OR z.status = :status)
              AND (:zoneType IS NULL OR z.zoneType = :zoneType)
            """)
    Page<ZoneJpaEntity> search(
            @Param("warehouseId") UUID warehouseId,
            @Param("status") WarehouseStatus status,
            @Param("zoneType") ZoneType zoneType,
            Pageable pageable);
}
