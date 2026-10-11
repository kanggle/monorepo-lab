package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneRefRepository extends JpaRepository<ZoneRefEntity, UUID> {

    Page<ZoneRefEntity> findAllBy(Pageable pageable);

    Page<ZoneRefEntity> findByWarehouseId(UUID warehouseId, Pageable pageable);

    /** § 1.7 search (TASK-BE-630). {@code q} is a {@link RefSearch#likePattern} or null. */
    @Query("SELECT z FROM ZoneRefEntity z "
            + "WHERE (:q IS NULL OR LOWER(z.zoneCode) LIKE :q ESCAPE '\\' "
            + "       OR LOWER(z.name) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR z.status = :status) "
            + "AND (:warehouseId IS NULL OR z.warehouseId = :warehouseId)")
    Page<ZoneRefEntity> search(@Param("q") String q,
                               @Param("status") String status,
                               @Param("warehouseId") UUID warehouseId,
                               Pageable pageable);
}
