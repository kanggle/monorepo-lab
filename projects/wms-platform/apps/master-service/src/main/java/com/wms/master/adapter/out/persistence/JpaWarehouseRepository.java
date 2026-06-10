package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.WarehouseStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaWarehouseRepository extends JpaRepository<WarehouseJpaEntity, UUID> {

    Optional<WarehouseJpaEntity> findByWarehouseCode(String warehouseCode);

    /**
     * Filter by optional status and optional case-insensitive substring across
     * warehouse_code and name. Null parameters mean "do not filter".
     */
    @Query("""
            SELECT w FROM WarehouseJpaEntity w
            WHERE (:status IS NULL OR w.status = :status)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(w.warehouseCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(w.name)          LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<WarehouseJpaEntity> search(
            @Param("status") WarehouseStatus status,
            @Param("q") String q,
            Pageable pageable);

    /**
     * ADR-MONO-025 (TASK-BE-349) data-scope-confined variant of {@link #search}:
     * identical filtering plus {@code AND w.warehouseCode IN :codes}. Invoked ONLY
     * for a deliberately-scoped operator with a non-empty code set, so the
     * {@code IN} list is never empty (an empty {@code IN ()} is invalid on some
     * dialects). The net-zero path uses the unchanged {@link #search} — keeping
     * the golden-path query byte-identical and risk-isolated.
     */
    @Query("""
            SELECT w FROM WarehouseJpaEntity w
            WHERE (:status IS NULL OR w.status = :status)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(w.warehouseCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(w.name)          LIKE LOWER(CONCAT('%', :q, '%')))
              AND w.warehouseCode IN :codes
            """)
    Page<WarehouseJpaEntity> searchScoped(
            @Param("status") WarehouseStatus status,
            @Param("q") String q,
            @Param("codes") Collection<String> codes,
            Pageable pageable);
}
