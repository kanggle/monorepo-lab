package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.department.Department;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepartmentJpaRepository extends JpaRepository<Department, String> {

    Optional<Department> findByIdAndTenantId(String id, String tenantId);

    Optional<Department> findByCodeAndTenantId(String code, String tenantId);

    List<Department> findAllByParentIdAndTenantIdAndStatus(String parentId, String tenantId,
                                                          com.example.erp.masterdata.domain.common.MasterStatus status);

    /**
     * Filtered page slice (masterdata-api.md § GET /departments). Each filter
     * param is nullable — a {@code null} disables that predicate. {@code asOf}
     * keeps only rows whose {@code [effectiveFrom, effectiveTo)} contains it
     * (start inclusive, end exclusive — mirrors {@code EffectivePeriod.contains}).
     */
    @Query("""
            SELECT d FROM Department d
            WHERE d.tenantId = :tenantId
              AND (:status IS NULL OR d.status = :status)
              AND (:parentId IS NULL OR d.parentId = :parentId)
              AND (:asOf IS NULL OR (d.effectiveFrom <= :asOf
                   AND (d.effectiveTo IS NULL OR :asOf < d.effectiveTo)))
            """)
    List<Department> findFiltered(@Param("tenantId") String tenantId,
                                  @Param("status") MasterStatus status,
                                  @Param("parentId") String parentId,
                                  @Param("asOf") LocalDate asOf,
                                  Pageable pageable);

    /** TRUE total-row count for {@link #findFiltered} (masterdata-api.md § PageMeta). */
    @Query("""
            SELECT COUNT(d) FROM Department d
            WHERE d.tenantId = :tenantId
              AND (:status IS NULL OR d.status = :status)
              AND (:parentId IS NULL OR d.parentId = :parentId)
              AND (:asOf IS NULL OR (d.effectiveFrom <= :asOf
                   AND (d.effectiveTo IS NULL OR :asOf < d.effectiveTo)))
            """)
    long countFiltered(@Param("tenantId") String tenantId,
                       @Param("status") MasterStatus status,
                       @Param("parentId") String parentId,
                       @Param("asOf") LocalDate asOf);
}
