package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaPartnerRepository extends JpaRepository<PartnerJpaEntity, UUID> {

    boolean existsByPartnerCode(String partnerCode);

    Optional<PartnerJpaEntity> findByPartnerCode(String partnerCode);

    /**
     * Filter by optional status, optional partnerType, and optional
     * case-insensitive substring against {@code name} and {@code partner_code}.
     * Null parameters mean "do not filter".
     */
    @Query("""
            SELECT p FROM PartnerJpaEntity p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:partnerType IS NULL OR p.partnerType = :partnerType)
              AND (:q IS NULL OR :q = ''
                   OR LOWER(p.name)        LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.partnerCode) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<PartnerJpaEntity> search(
            @Param("status") WarehouseStatus status,
            @Param("partnerType") PartnerType partnerType,
            @Param("q") String q,
            Pageable pageable);
}
