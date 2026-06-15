package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.port.outbound.OrgViewMetricsPort;
import com.example.erp.readmodel.application.query.EmployeeOrgViewPage;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;
import com.example.erp.readmodel.domain.projection.CostCenterProjection;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.JobGradeProjection;
import com.example.erp.readmodel.domain.projection.repository.CostCenterProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.JobGradeProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles the integrated employee org-view at READ time (E5 read-only). The
 * employee projection is joined with its referenced department (resolved with a
 * root→leaf ancestry walk over {@code parent_id}), cost center, and job grade.
 * A referenced master that has not yet been consumed resolves to {@code null}
 * and is recorded in {@code meta.unresolved} — never fabricated (E5;
 * {@code READ_MODEL_SOURCE_UNAVAILABLE} semantics).
 *
 * <p>{@code asOf} is accepted by the controller for E2 point-in-time parity; the
 * first increment's projection holds the latest revision per aggregate, so the
 * resolved values are the projected latest (the retained {@code RETIRED} rows
 * are still resolvable). Deeper revision-history reads are a follow-up.
 */
@Service
@RequiredArgsConstructor
public class QueryEmployeeOrgViewUseCase {

    private final EmployeeProjectionRepository employeeRepository;
    private final DepartmentProjectionRepository departmentRepository;
    private final CostCenterProjectionRepository costCenterRepository;
    private final JobGradeProjectionRepository jobGradeRepository;
    private final OrgViewMetricsPort metrics;
    private final OrgScopeExpander orgScopeExpander;
    private final DepartmentPathResolver departmentPathResolver;

    @Value("${erpplatform.readmodel.department-path-max-depth:32}")
    private int departmentPathMaxDepth;

    /** Single employee org-view; 404 {@code MASTERDATA_NOT_FOUND} on a projection miss. */
    @Transactional(readOnly = true)
    public EmployeeOrgView getOne(String employeeId) {
        return getOne(employeeId, null);
    }

    /**
     * Single employee org-view with an optional {@code org_scope} read filter
     * (TASK-ERP-BE-008). When {@code orgScopeRootIds} is non-null (a bounded
     * operator scope), an employee whose resolved department is NOT within the
     * union of those subtree-roots is reported as a <b>404
     * {@code MASTERDATA_NOT_FOUND}</b> — the data-scope boundary does not leak
     * the employee's existence (architecture.md § Multi-tenancy; symmetric with
     * masterdata-service's write deny). {@code null} = no narrowing (net-zero).
     *
     * <p>Conservative exclusion (E5): an employee whose department is not (yet)
     * projected cannot be proved in-scope, so it is treated as out-of-scope
     * (404) under a bounded scope — never fabricated as visible.
     */
    @Transactional(readOnly = true)
    public EmployeeOrgView getOne(String employeeId, List<String> orgScopeRootIds) {
        EmployeeProjection employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ReadModelNotFoundException(employeeId));
        if (orgScopeRootIds != null
                && !isWithinOrgScope(employee.departmentId(), orgScopeRootIds)) {
            // Out of the operator's org_scope subtree → 404 (do not leak existence).
            throw new ReadModelNotFoundException(employeeId);
        }
        return assemble(employee);
    }

    /** Paginated employee org-view list (status + optional department-subtree filter). */
    @Transactional(readOnly = true)
    public EmployeeOrgViewPage list(MasterStatus status, String departmentId, int page, int size) {
        return list(status, departmentId, null, page, size);
    }

    /**
     * Paginated employee org-view list with the {@code org_scope} read filter
     * (TASK-ERP-BE-008). The optional explicit {@code departmentId} subtree
     * filter is composed (intersected) with the operator's {@code org_scope}
     * subtree-root expansion: the resulting page is the employees whose
     * department is in BOTH (when both are present). {@code orgScopeRootIds ==
     * null} = no org_scope narrowing (net-zero, BE-007 behavior); a non-null but
     * empty/zero-scope expands to an empty id set → an empty page (fail-closed).
     */
    @Transactional(readOnly = true)
    public EmployeeOrgViewPage list(MasterStatus status, String departmentId,
                                    List<String> orgScopeRootIds, int page, int size) {
        MasterStatus effectiveStatus = status == null ? MasterStatus.ACTIVE : status;
        List<String> explicitSubtree = null;
        if (departmentId != null && !departmentId.isBlank()) {
            explicitSubtree = departmentRepository.findSubtreeIds(departmentId, departmentPathMaxDepth);
        }
        List<String> orgScopeSubtree = orgScopeExpander.expand(orgScopeRootIds);
        List<String> subtreeIds = intersectFilters(explicitSubtree, orgScopeSubtree);
        List<EmployeeProjection> employees =
                employeeRepository.findPage(effectiveStatus, subtreeIds, page, size);
        long total = employeeRepository.count(effectiveStatus, subtreeIds);

        // Batch-resolve the references for the page (avoid N+1).
        Set<String> deptIds = new HashSet<>();
        Set<String> ccIds = new HashSet<>();
        Set<String> jgIds = new HashSet<>();
        for (EmployeeProjection e : employees) {
            if (e.departmentId() != null) deptIds.add(e.departmentId());
            if (e.costCenterId() != null) ccIds.add(e.costCenterId());
            if (e.jobGradeId() != null) jgIds.add(e.jobGradeId());
        }
        Map<String, DepartmentProjection> departments = departmentRepository.findAllByIds(deptIds);
        Map<String, CostCenterProjection> costCenters = costCenterRepository.findAllByIds(ccIds);
        Map<String, JobGradeProjection> jobGrades = jobGradeRepository.findAllByIds(jgIds);

        List<EmployeeOrgView> content = new ArrayList<>(employees.size());
        for (EmployeeProjection e : employees) {
            content.add(assembleWith(e, departments, costCenters, jobGrades, true));
        }
        return new EmployeeOrgViewPage(content, page, size, total);
    }

    // ------------------------------------------------------------------------
    // Assembly
    // ------------------------------------------------------------------------

    private EmployeeOrgView assemble(EmployeeProjection employee) {
        Map<String, DepartmentProjection> departments = employee.departmentId() == null
                ? Map.of()
                : departmentRepository.findAllByIds(List.of(employee.departmentId()));
        Map<String, CostCenterProjection> costCenters = employee.costCenterId() == null
                ? Map.of()
                : costCenterRepository.findAllByIds(List.of(employee.costCenterId()));
        Map<String, JobGradeProjection> jobGrades = employee.jobGradeId() == null
                ? Map.of()
                : jobGradeRepository.findAllByIds(List.of(employee.jobGradeId()));
        return assembleWith(employee, departments, costCenters, jobGrades, true);
    }

    private EmployeeOrgView assembleWith(EmployeeProjection employee,
                                         Map<String, DepartmentProjection> departments,
                                         Map<String, CostCenterProjection> costCenters,
                                         Map<String, JobGradeProjection> jobGrades,
                                         boolean recordMetrics) {
        EmployeeOrgView.Builder builder = new EmployeeOrgView.Builder(
                employee.id(), employee.employeeNumber(), employee.name(), employee.status(),
                employee.effectiveFrom(), employee.effectiveTo());

        // Department (with ancestry path).
        EmployeeOrgView.DepartmentRef deptRef = null;
        boolean deptMissing = false;
        if (employee.departmentId() != null) {
            DepartmentProjection dept = departments.get(employee.departmentId());
            if (dept != null) {
                deptRef = new EmployeeOrgView.DepartmentRef(
                        dept.id(), dept.code(), dept.name(), resolvePath(dept));
            } else {
                deptMissing = true;
                if (recordMetrics) metrics.recordUnresolved(EmployeeOrgView.REF_DEPARTMENT);
            }
        }
        builder.department(deptRef, deptMissing);

        // Cost center.
        EmployeeOrgView.CostCenterRef ccRef = null;
        boolean ccMissing = false;
        if (employee.costCenterId() != null) {
            CostCenterProjection cc = costCenters.get(employee.costCenterId());
            if (cc != null) {
                ccRef = new EmployeeOrgView.CostCenterRef(cc.id(), cc.code(), cc.name());
            } else {
                ccMissing = true;
                if (recordMetrics) metrics.recordUnresolved(EmployeeOrgView.REF_COST_CENTER);
            }
        }
        builder.costCenter(ccRef, ccMissing);

        // Job grade.
        EmployeeOrgView.JobGradeRef jgRef = null;
        boolean jgMissing = false;
        if (employee.jobGradeId() != null) {
            JobGradeProjection jg = jobGrades.get(employee.jobGradeId());
            if (jg != null) {
                jgRef = new EmployeeOrgView.JobGradeRef(jg.id(), jg.code(), jg.name(),
                        jg.displayOrder());
            } else {
                jgMissing = true;
                if (recordMetrics) metrics.recordUnresolved(EmployeeOrgView.REF_JOB_GRADE);
            }
        }
        builder.jobGrade(jgRef, jgMissing);

        return builder.build();
    }

    /**
     * Resolves the root→leaf department ancestry path (delegates to the shared
     * {@link DepartmentPathResolver}; maps the neutral nodes to this view's
     * {@link EmployeeOrgView.PathNode}). Net-zero — the walk semantics are
     * preserved verbatim in the collaborator.
     */
    private List<EmployeeOrgView.PathNode> resolvePath(DepartmentProjection leaf) {
        List<EmployeeOrgView.PathNode> path = new ArrayList<>();
        for (DepartmentPathResolver.Node node : departmentPathResolver.resolvePath(leaf)) {
            path.add(new EmployeeOrgView.PathNode(node.id(), node.code(), node.name()));
        }
        return path;
    }

    // ------------------------------------------------------------------------
    // org_scope read filter (TASK-ERP-BE-008)
    // ------------------------------------------------------------------------

    /**
     * Intersects the explicit {@code ?departmentId=} subtree filter with the
     * {@code org_scope} subtree filter. Either may be {@code null} (= not
     * applied). When both are present the result is their intersection (the
     * operator sees only the requested subtree AND only within their scope).
     */
    private List<String> intersectFilters(List<String> explicit, List<String> orgScope) {
        if (explicit == null) {
            return orgScope;
        }
        if (orgScope == null) {
            return explicit;
        }
        Set<String> intersection = new LinkedHashSet<>(explicit);
        intersection.retainAll(new HashSet<>(orgScope));
        return new ArrayList<>(intersection);
    }

    /**
     * {@code true} iff {@code departmentId} is within the union of the
     * {@code org_scope} subtree-roots' subtrees. A null/unprojected department
     * is NOT within a bounded scope (conservative exclusion, E5 — cannot prove
     * in-scope).
     */
    private boolean isWithinOrgScope(String departmentId, List<String> orgScopeRootIds) {
        if (departmentId == null) {
            return false;
        }
        List<String> expanded = orgScopeExpander.expand(orgScopeRootIds);
        return expanded != null && expanded.contains(departmentId);
    }
}
