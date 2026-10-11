package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkuRefRepository extends JpaRepository<SkuRefEntity, UUID> {

    Page<SkuRefEntity> findAllBy(Pageable pageable);

    /** § 1.7 search (TASK-BE-630). {@code q} is a {@link RefSearch#likePattern} or null. */
    @Query("SELECT s FROM SkuRefEntity s "
            + "WHERE (:q IS NULL OR LOWER(s.skuCode) LIKE :q ESCAPE '\\' "
            + "       OR LOWER(s.name) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR s.status = :status)")
    Page<SkuRefEntity> search(@Param("q") String q,
                              @Param("status") String status,
                              Pageable pageable);
}
