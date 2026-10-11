package com.wms.admin.readmodel.master;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRefRepository extends JpaRepository<PartnerRefEntity, UUID> {

    Page<PartnerRefEntity> findAllBy(Pageable pageable);

    /**
     * § 1.7 search (TASK-BE-630). {@code partnerTypes} is never empty — see
     * {@link RefSearch#types}; {@code anyType = true} means "no type filter".
     */
    @Query("SELECT p FROM PartnerRefEntity p "
            + "WHERE (:q IS NULL OR LOWER(p.partnerCode) LIKE :q ESCAPE '\\' "
            + "       OR LOWER(p.name) LIKE :q ESCAPE '\\') "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (:anyType = true OR p.partnerType IN :partnerTypes)")
    Page<PartnerRefEntity> search(@Param("q") String q,
                                  @Param("status") String status,
                                  @Param("anyType") boolean anyType,
                                  @Param("partnerTypes") List<String> partnerTypes,
                                  Pageable pageable);
}
