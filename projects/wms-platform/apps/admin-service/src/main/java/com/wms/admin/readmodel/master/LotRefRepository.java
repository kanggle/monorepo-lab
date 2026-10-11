package com.wms.admin.readmodel.master;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotRefRepository extends JpaRepository<LotRefEntity, UUID> {

    Page<LotRefEntity> findAllBy(Pageable pageable);

    Page<LotRefEntity> findBySkuId(UUID skuId, Pageable pageable);

    /**
     * § 1.7 search (TASK-BE-630). A lot has no name — {@code q} matches {@code lotNo}
     * only. A lot number is only meaningful within its SKU, hence {@code skuId}.
     */
    @Query("SELECT l FROM LotRefEntity l "
            + "WHERE (:q IS NULL OR LOWER(l.lotNo) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR l.status = :status) "
            + "AND (:skuId IS NULL OR l.skuId = :skuId)")
    Page<LotRefEntity> search(@Param("q") String q,
                              @Param("status") String status,
                              @Param("skuId") UUID skuId,
                              Pageable pageable);
}
