package com.example.erp.masterdata.domain.department.repository;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.department.Department;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for Department persistence (Hexagonal). Every lookup is
 * tenant-scoped — there is no tenant-omitting method (architecture.md §
 * Multi-tenancy fail-closed).
 */
public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findById(String id, String tenantId);

    Optional<Department> findByCode(String code, String tenantId);

    /**
     * Filtered, paginated list with the TRUE total-row count
     * (masterdata-api.md § GET /departments + § PageMeta). The returned
     * {@link PageResult#totalElements()} counts ALL rows matching
     * {@code filter}, not just the page slice.
     */
    PageResult<Department> findAll(String tenantId, DepartmentListFilter filter, int page, int size);

    /** Children of {@code parentId} (for retire reference-integrity guard). */
    List<Department> findActiveChildren(String parentId, String tenantId);

    /** Walk ancestry — used by move-parent cycle guard. */
    List<Department> ancestors(String departmentId, String tenantId);

    /**
     * Subtree expansion for a scoped LIST (TASK-ERP-BE-045): {@code rootIds} ∪ every descendant of any of them, in
     * this tenant. Same containment as the single-target walk ({@link #ancestors}): a department is in iff it or an
     * ancestor is a root; department status / effective dating are NOT consulted; a root id matching no department
     * still counts as itself. Cycle-safe. Empty {@code rootIds} → empty set.
     */
    Set<String> subtreeIds(Set<String> rootIds, String tenantId);

    /** Batch lookup by id in this tenant (unknown ids are simply absent from the result). */
    List<Department> findAllByIds(Collection<String> ids, String tenantId);
}
