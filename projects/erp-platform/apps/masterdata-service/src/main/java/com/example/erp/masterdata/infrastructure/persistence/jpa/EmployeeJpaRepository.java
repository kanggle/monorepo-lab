package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.employee.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeJpaRepository extends JpaRepository<Employee, String> {

    Optional<Employee> findByIdAndTenantId(String id, String tenantId);

    Optional<Employee> findByEmployeeNumberAndTenantId(String employeeNumber, String tenantId);

    Optional<Employee> findByAccountIdAndTenantId(String accountId, String tenantId);

    List<Employee> findAllByDepartmentIdAndTenantIdAndStatus(String departmentId, String tenantId,
                                                            MasterStatus status);

    List<Employee> findAllByCostCenterIdAndTenantIdAndStatus(String costCenterId, String tenantId,
                                                             MasterStatus status);

    List<Employee> findAllByJobGradeIdAndTenantIdAndStatus(String jobGradeId, String tenantId,
                                                          MasterStatus status);

    /** Filtered page slice (masterdata-api.md § GET /employees). Nullable filter params. */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
              AND (:departmentId IS NULL OR e.departmentId = :departmentId)
              AND (:costCenterId IS NULL OR e.costCenterId = :costCenterId)
              AND (:asOf IS NULL OR (e.effectiveFrom <= :asOf
                   AND (e.effectiveTo IS NULL OR :asOf < e.effectiveTo)))
            """)
    List<Employee> findFiltered(@Param("tenantId") String tenantId,
                                @Param("status") MasterStatus status,
                                @Param("departmentId") String departmentId,
                                @Param("costCenterId") String costCenterId,
                                @Param("asOf") LocalDate asOf,
                                Pageable pageable);

    /** TRUE total-row count for {@link #findFiltered} (masterdata-api.md § PageMeta). */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
              AND (:departmentId IS NULL OR e.departmentId = :departmentId)
              AND (:costCenterId IS NULL OR e.costCenterId = :costCenterId)
              AND (:asOf IS NULL OR (e.effectiveFrom <= :asOf
                   AND (e.effectiveTo IS NULL OR :asOf < e.effectiveTo)))
            """)
    long countFiltered(@Param("tenantId") String tenantId,
                       @Param("status") MasterStatus status,
                       @Param("departmentId") String departmentId,
                       @Param("costCenterId") String costCenterId,
                       @Param("asOf") LocalDate asOf);

    // ---- TASK-ERP-BE-045 employee picker (masterdata-api.md § GET /employees/picker) ----------------------
    // Two variants instead of a nullable collection parameter: «whole tenant» and «these departments» must never
    // be told apart by a NULL check on a collection (an empty set would silently mean «no filter»).

    @Query("""
            SELECT e FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
            ORDER BY e.employeeNumber ASC
            """)
    List<Employee> findPickerInTenant(@Param("tenantId") String tenantId,
                                      @Param("status") MasterStatus status,
                                      Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
            """)
    long countPickerInTenant(@Param("tenantId") String tenantId,
                             @Param("status") MasterStatus status);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
              AND e.departmentId IN :departmentIds
            ORDER BY e.employeeNumber ASC
            """)
    List<Employee> findPickerInDepartments(@Param("tenantId") String tenantId,
                                           @Param("status") MasterStatus status,
                                           @Param("departmentIds") Collection<String> departmentIds,
                                           Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.tenantId = :tenantId
              AND (:status IS NULL OR e.status = :status)
              AND e.departmentId IN :departmentIds
            """)
    long countPickerInDepartments(@Param("tenantId") String tenantId,
                                  @Param("status") MasterStatus status,
                                  @Param("departmentIds") Collection<String> departmentIds);
}
