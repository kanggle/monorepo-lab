package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobGradeJpaRepository extends JpaRepository<JobGrade, String> {

    Optional<JobGrade> findByIdAndTenantId(String id, String tenantId);

    Optional<JobGrade> findByCodeAndTenantId(String code, String tenantId);

    /** Filtered page slice (masterdata-api.md § GET /job-grades). Nullable filter params. */
    @Query("""
            SELECT g FROM JobGrade g
            WHERE g.tenantId = :tenantId
              AND (:status IS NULL OR g.status = :status)
              AND (:asOf IS NULL OR (g.effectiveFrom <= :asOf
                   AND (g.effectiveTo IS NULL OR :asOf < g.effectiveTo)))
            """)
    List<JobGrade> findFiltered(@Param("tenantId") String tenantId,
                                @Param("status") MasterStatus status,
                                @Param("asOf") LocalDate asOf,
                                Pageable pageable);

    /** TRUE total-row count for {@link #findFiltered} (masterdata-api.md § PageMeta). */
    @Query("""
            SELECT COUNT(g) FROM JobGrade g
            WHERE g.tenantId = :tenantId
              AND (:status IS NULL OR g.status = :status)
              AND (:asOf IS NULL OR (g.effectiveFrom <= :asOf
                   AND (g.effectiveTo IS NULL OR :asOf < g.effectiveTo)))
            """)
    long countFiltered(@Param("tenantId") String tenantId,
                       @Param("status") MasterStatus status,
                       @Param("asOf") LocalDate asOf);
}
