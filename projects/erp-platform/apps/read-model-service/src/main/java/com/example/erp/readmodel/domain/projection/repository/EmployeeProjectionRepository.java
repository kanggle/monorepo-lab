package com.example.erp.readmodel.domain.projection.repository;

import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository port for {@link EmployeeProjection}. */
public interface EmployeeProjectionRepository {

    Optional<EmployeeProjection> findById(String id);

    /**
     * Paginated employee projection page filtered by {@code status} and,
     * optionally, restricted to a set of department ids (subtree filter). When
     * {@code departmentIds} is {@code null} the department filter is not applied;
     * when it is empty the page is empty (an empty subtree matches nothing).
     */
    List<EmployeeProjection> findPage(MasterStatus status, List<String> departmentIds,
                                      int page, int size);

    long count(MasterStatus status, List<String> departmentIds);

    /**
     * Employee ids whose {@code departmentId} is in the given set — used by the
     * approval-fact org_scope read filter to resolve EMPLOYEE-subject scope
     * (the approval subject is an employee; its in-scope-ness is its department's
     * scope). An empty input → an empty result.
     */
    List<String> findIdsByDepartmentIdIn(Collection<String> departmentIds);

    void save(EmployeeProjection projection);
}
