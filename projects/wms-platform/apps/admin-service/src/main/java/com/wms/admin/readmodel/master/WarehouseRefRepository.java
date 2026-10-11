package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRefRepository extends JpaRepository<WarehouseRefEntity, UUID> {

    Page<WarehouseRefEntity> findAllBy(Pageable pageable);

    /** § 1.7 search (TASK-BE-630). {@code q} is a {@link RefSearch#likePattern} or null. */
    @Query("SELECT w FROM WarehouseRefEntity w "
            + "WHERE (:q IS NULL OR LOWER(w.warehouseCode) LIKE :q ESCAPE '\\' "
            + "       OR LOWER(w.name) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR w.status = :status)")
    Page<WarehouseRefEntity> search(@Param("q") String q,
                                    @Param("status") String status,
                                    Pageable pageable);
}
