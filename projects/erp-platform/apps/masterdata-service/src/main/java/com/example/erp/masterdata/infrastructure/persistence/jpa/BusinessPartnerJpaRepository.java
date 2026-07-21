package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.businesspartner.PartnerType;
import com.example.erp.masterdata.domain.common.MasterStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusinessPartnerJpaRepository extends JpaRepository<BusinessPartner, String> {

    Optional<BusinessPartner> findByIdAndTenantId(String id, String tenantId);

    Optional<BusinessPartner> findByCodeAndTenantId(String code, String tenantId);

    /** Filtered page slice (masterdata-api.md § GET /business-partners). Nullable filter params. */
    @Query("""
            SELECT b FROM BusinessPartner b
            WHERE b.tenantId = :tenantId
              AND (:status IS NULL OR b.status = :status)
              AND (:partnerType IS NULL OR b.partnerType = :partnerType)
              AND (:asOf IS NULL OR (b.effectiveFrom <= :asOf
                   AND (b.effectiveTo IS NULL OR :asOf < b.effectiveTo)))
            """)
    List<BusinessPartner> findFiltered(@Param("tenantId") String tenantId,
                                       @Param("status") MasterStatus status,
                                       @Param("partnerType") PartnerType partnerType,
                                       @Param("asOf") LocalDate asOf,
                                       Pageable pageable);

    /** TRUE total-row count for {@link #findFiltered} (masterdata-api.md § PageMeta). */
    @Query("""
            SELECT COUNT(b) FROM BusinessPartner b
            WHERE b.tenantId = :tenantId
              AND (:status IS NULL OR b.status = :status)
              AND (:partnerType IS NULL OR b.partnerType = :partnerType)
              AND (:asOf IS NULL OR (b.effectiveFrom <= :asOf
                   AND (b.effectiveTo IS NULL OR :asOf < b.effectiveTo)))
            """)
    long countFiltered(@Param("tenantId") String tenantId,
                       @Param("status") MasterStatus status,
                       @Param("partnerType") PartnerType partnerType,
                       @Param("asOf") LocalDate asOf);
}
