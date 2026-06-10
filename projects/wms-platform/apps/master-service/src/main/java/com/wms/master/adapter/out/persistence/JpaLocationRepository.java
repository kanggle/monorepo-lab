package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaLocationRepository extends JpaRepository<LocationJpaEntity, UUID> {

    boolean existsByLocationCode(String locationCode);

    /**
     * Used by {@code ZonePersistenceAdapter.hasActiveLocationsFor} to enforce
     * the "no active child Location" invariant on Zone deactivate. Matches the
     * guard specified in TASK-BE-003.
     */
    boolean existsByZoneIdAndStatus(UUID zoneId, WarehouseStatus status);

    Optional<LocationJpaEntity> findByLocationCode(String locationCode);

    /**
     * Flat list search. Null filter parameters are treated as "do not filter"
     * for that column.
     */
    @Query("""
            SELECT l FROM LocationJpaEntity l
            WHERE (:warehouseId IS NULL OR l.warehouseId = :warehouseId)
              AND (:zoneId IS NULL OR l.zoneId = :zoneId)
              AND (:locationType IS NULL OR l.locationType = :locationType)
              AND (:locationCode IS NULL OR l.locationCode = :locationCode)
              AND (:status IS NULL OR l.status = :status)
            """)
    Page<LocationJpaEntity> search(
            @Param("warehouseId") UUID warehouseId,
            @Param("zoneId") UUID zoneId,
            @Param("locationType") LocationType locationType,
            @Param("locationCode") String locationCode,
            @Param("status") WarehouseStatus status,
            Pageable pageable);

    /**
     * ADR-MONO-025 (TASK-BE-350) data-scope-confined variant of {@link #search}:
     * identical filtering plus a parent-warehouse-code confinement. Scope tokens
     * are warehouse <b>codes</b>, but locations reference the warehouse by id, so
     * a correlated subquery resolves codes→ids in one statement (no service-side
     * resolution, no extra warehouse port method). Invoked ONLY for a deliberately
     * scoped operator with a non-empty {@code codes} set, so the inner
     * {@code IN :codes} is never empty; an unmatched code simply yields an empty
     * subquery (valid SQL — correct deny-all). The net-zero path uses the
     * unchanged {@link #search}.
     */
    @Query("""
            SELECT l FROM LocationJpaEntity l
            WHERE (:warehouseId IS NULL OR l.warehouseId = :warehouseId)
              AND (:zoneId IS NULL OR l.zoneId = :zoneId)
              AND (:locationType IS NULL OR l.locationType = :locationType)
              AND (:locationCode IS NULL OR l.locationCode = :locationCode)
              AND (:status IS NULL OR l.status = :status)
              AND l.warehouseId IN (
                  SELECT w.id FROM WarehouseJpaEntity w WHERE w.warehouseCode IN :codes)
            """)
    Page<LocationJpaEntity> searchScoped(
            @Param("warehouseId") UUID warehouseId,
            @Param("zoneId") UUID zoneId,
            @Param("locationType") LocationType locationType,
            @Param("locationCode") String locationCode,
            @Param("status") WarehouseStatus status,
            @Param("codes") Collection<String> codes,
            Pageable pageable);
}
