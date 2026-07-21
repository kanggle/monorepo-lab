package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CostCenterJpaRepository extends JpaRepository<CostCenter, String> {

    Optional<CostCenter> findByIdAndTenantId(String id, String tenantId);

    Optional<CostCenter> findByCodeAndTenantId(String code, String tenantId);

    List<CostCenter> findAllByDepartmentIdAndTenantIdAndStatus(String departmentId, String tenantId,
                                                               MasterStatus status);

    /** Filtered page slice (masterdata-api.md § GET /cost-centers). Nullable filter params. */
    @Query("""
            SELECT c FROM CostCenter c
            WHERE c.tenantId = :tenantId
              AND (:status IS NULL OR c.status = :status)
              AND (:departmentId IS NULL OR c.departmentId = :departmentId)
              AND (:asOf IS NULL OR (c.effectiveFrom <= :asOf
                   AND (c.effectiveTo IS NULL OR :asOf < c.effectiveTo)))
            """)
    List<CostCenter> findFiltered(@Param("tenantId") String tenantId,
                                  @Param("status") MasterStatus status,
                                  @Param("departmentId") String departmentId,
                                  @Param("asOf") LocalDate asOf,
                                  Pageable pageable);

    /** TRUE total-row count for {@link #findFiltered} (masterdata-api.md § PageMeta). */
    @Query("""
            SELECT COUNT(c) FROM CostCenter c
            WHERE c.tenantId = :tenantId
              AND (:status IS NULL OR c.status = :status)
              AND (:departmentId IS NULL OR c.departmentId = :departmentId)
              AND (:asOf IS NULL OR (c.effectiveFrom <= :asOf
                   AND (c.effectiveTo IS NULL OR :asOf < c.effectiveTo)))
            """)
    long countFiltered(@Param("tenantId") String tenantId,
                       @Param("status") MasterStatus status,
                       @Param("departmentId") String departmentId,
                       @Param("asOf") LocalDate asOf);
}
