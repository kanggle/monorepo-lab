package com.example.erp.masterdata.domain.department.repository;

import com.example.erp.masterdata.domain.department.Department;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Department persistence (Hexagonal). Every lookup is
 * tenant-scoped — there is no tenant-omitting method (architecture.md §
 * Multi-tenancy fail-closed).
 */
public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findById(String id, String tenantId);

    Optional<Department> findByCode(String code, String tenantId);

    List<Department> findAll(String tenantId, int page, int size);

    /** Children of {@code parentId} (for retire reference-integrity guard). */
    List<Department> findActiveChildren(String parentId, String tenantId);

    /** Walk ancestry — used by move-parent cycle guard. */
    List<Department> ancestors(String departmentId, String tenantId);
}
