package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRefRepository extends JpaRepository<LocationRefEntity, UUID> {

    Page<LocationRefEntity> findAllBy(Pageable pageable);

    Page<LocationRefEntity> findByWarehouseId(UUID warehouseId, Pageable pageable);

    /**
     * § 1.7 search (TASK-BE-630). A location has no name — {@code q} matches
     * {@code locationCode} only (the code printed on shelf labels).
     */
    @Query("SELECT l FROM LocationRefEntity l "
            + "WHERE (:q IS NULL OR LOWER(l.locationCode) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR l.status = :status) "
            + "AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId)")
    Page<LocationRefEntity> search(@Param("q") String q,
                                   @Param("status") String status,
                                   @Param("warehouseId") UUID warehouseId,
                                   Pageable pageable);
}
