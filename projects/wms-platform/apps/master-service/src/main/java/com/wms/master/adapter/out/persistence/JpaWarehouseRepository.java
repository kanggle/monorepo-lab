package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.WarehouseStatus;
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
}
