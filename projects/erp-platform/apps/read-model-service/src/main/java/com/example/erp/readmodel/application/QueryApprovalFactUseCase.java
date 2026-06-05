package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.port.outbound.OrgViewMetricsPort;
import com.example.erp.readmodel.application.query.ApprovalFactPage;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalFactView;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactFilter;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactProjectionRepository;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Serves the read-only approval-fact API (E5 — latest fact only; the
 * authoritative transition history stays with {@code approval-service}). The
 * subject ref is resolved at READ time against {@code department_proj} /
 * {@code employee_proj} (DEPARTMENT → a {@code DepartmentSubjectRef} with the
 * ancestry path; EMPLOYEE → a trimmed employee ref) — an unconsumed master
 * resolves to {@code subject:null} + {@code meta.unresolved:["subject"]}, never
 * fabricated.
 *
 * <p><b>org_scope read filter (TASK-ERP-BE-008 parity).</b> The list/detail are
 * narrowed to facts whose <b>subject department</b> is within the operator's
 * {@code org_scope} subtree: a {@code DEPARTMENT} subject is its own department;
 * an {@code EMPLOYEE} subject resolves its department via {@code employee_proj}.
 * {@code orgScopeRootIds == null} (platform/{@code ["*"]}/absent) = no narrowing
 * (net-zero). Out-of-scope detail → 404 (no existence leak, mirrors the org-view
 * detail rule).
 */
@Service
@RequiredArgsConstructor
public class QueryApprovalFactUseCase {

    private final ApprovalFactProjectionRepository approvalRepository;
    private final DepartmentProjectionRepository departmentRepository;
    private final EmployeeProjectionRepository employeeRepository;
    private final OrgViewMetricsPort metrics;

    @Value("${erpplatform.readmodel.department-path-max-depth:32}")
    private int departmentPathMaxDepth;

    // ------------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------------

    /** Single approval fact; 404 {@code MASTERDATA_NOT_FOUND} on a projection miss. */
    @Transactional(readOnly = true)
    public ApprovalFactView getOne(String approvalRequestId) {
        return getOne(approvalRequestId, null);
    }

    /**
     * Single approval fact with an optional {@code org_scope} read filter. When
     * {@code orgScopeRootIds} is non-null (a bounded operator scope) and the
     * fact's subject department is NOT within the union of those subtree-roots,
     * the fact is reported as a <b>404 {@code MASTERDATA_NOT_FOUND}</b> — the
     * data-scope boundary does not leak the request's existence (symmetric with
     * the org-view detail rule). {@code null} = no narrowing (net-zero).
     *
     * <p>Conservative exclusion (E5): a subject whose department cannot be
     * resolved (unprojected) cannot be proved in-scope, so under a bounded scope
     * it is treated as out-of-scope (404) — never fabricated as visible.
     */
    @Transactional(readOnly = true)
    public ApprovalFactView getOne(String approvalRequestId, List<String> orgScopeRootIds) {
        ApprovalFactProjection fact = approvalRepository.findById(approvalRequestId)
                .orElseThrow(() -> new ReadModelNotFoundException(approvalRequestId));
        if (orgScopeRootIds != null
                && !isWithinOrgScope(fact, expandOrgScope(orgScopeRootIds))) {
            throw new ReadModelNotFoundException(approvalRequestId);
        }
        return resolveSubject(fact, true);
    }

    // ------------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------------

    /** Paginated approval-fact list with the explicit filters + the org_scope read filter. */
    @Transactional(readOnly = true)
    public ApprovalFactPage list(ApprovalStatus status, ApprovalSubjectType subjectType,
                                 String subjectId, String approverId, String submitterId,
                                 List<String> orgScopeRootIds, int page, int size) {
        ApprovalFactFilter filter;
        if (orgScopeRootIds == null) {
            // Net-zero: no org_scope narrowing.
            filter = ApprovalFactFilter.unbounded(status, subjectType, subjectId,
                    approverId, submitterId);
        } else {
            List<String> scopedDepartmentIds = expandOrgScope(orgScopeRootIds);
            // EMPLOYEE subjects in scope = employees whose department is in scope.
            List<String> scopedEmployeeSubjectIds =
                    employeeRepository.findIdsByDepartmentIdIn(scopedDepartmentIds);
            filter = new ApprovalFactFilter(status, subjectType, subjectId, approverId,
                    submitterId, false, scopedDepartmentIds, scopedEmployeeSubjectIds);
        }

        List<ApprovalFactProjection> facts = approvalRepository.findPage(filter, page, size);
        long total = approvalRepository.count(filter);
        List<ApprovalFactView> content = new ArrayList<>(facts.size());
        for (ApprovalFactProjection fact : facts) {
            content.add(resolveSubject(fact, true));
        }
        return new ApprovalFactPage(content, page, size, total);
    }

    // ------------------------------------------------------------------------
    // Subject resolution (read-time)
    // ------------------------------------------------------------------------

    private ApprovalFactView resolveSubject(ApprovalFactProjection fact, boolean recordMetrics) {
        if (fact.subjectType() == ApprovalSubjectType.DEPARTMENT) {
            DepartmentProjection dept = fact.subjectId() == null ? null
                    : departmentRepository.findById(fact.subjectId()).orElse(null);
            if (dept == null) {
                if (recordMetrics) metrics.recordUnresolved(ApprovalFactView.REF_SUBJECT);
                return ApprovalFactView.ofUnresolvedSubject(fact);
            }
            return ApprovalFactView.ofDepartment(fact,
                    new ApprovalFactView.DepartmentSubjectRef(
                            dept.id(), dept.code(), dept.name(), resolvePath(dept)));
        }
        if (fact.subjectType() == ApprovalSubjectType.EMPLOYEE) {
            EmployeeProjection emp = fact.subjectId() == null ? null
                    : employeeRepository.findById(fact.subjectId()).orElse(null);
            if (emp == null) {
                if (recordMetrics) metrics.recordUnresolved(ApprovalFactView.REF_SUBJECT);
                return ApprovalFactView.ofUnresolvedSubject(fact);
            }
            return ApprovalFactView.ofEmployee(fact,
                    new ApprovalFactView.EmployeeSubjectRef(
                            emp.id(), emp.employeeNumber(), emp.name()));
        }
        // Unknown/absent subject type — cannot resolve a ref (surface unresolved).
        if (recordMetrics) metrics.recordUnresolved(ApprovalFactView.REF_SUBJECT);
        return ApprovalFactView.ofUnresolvedSubject(fact);
    }

    /**
     * Resolves the root→leaf department ancestry path by walking {@code parentId}
     * (depth-bounded, cycle-guarded — same walk as the employee org-view).
     */
    private List<ApprovalFactView.PathNode> resolvePath(DepartmentProjection leaf) {
        java.util.Deque<ApprovalFactView.PathNode> reversed = new java.util.ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        DepartmentProjection current = leaf;
        int depth = 0;
        while (current != null && depth < departmentPathMaxDepth && visited.add(current.id())) {
            reversed.addFirst(new ApprovalFactView.PathNode(
                    current.id(), current.code(), current.name()));
            String parentId = current.parentId();
            if (parentId == null || parentId.isBlank()) {
                break;
            }
            current = departmentRepository.findById(parentId).orElse(null);
            depth++;
        }
        return new ArrayList<>(reversed);
    }

    // ------------------------------------------------------------------------
    // org_scope helpers (subject department → subtree containment)
    // ------------------------------------------------------------------------

    /**
     * Expands {@code org_scope} subtree-roots → the union of their descendant
     * department ids over {@code department_proj.parent_id}. {@code null} →
     * {@code null} (no narrowing); a bounded scope with no roots → an empty list
     * (zero-scope, matches nothing).
     */
    private List<String> expandOrgScope(List<String> orgScopeRootIds) {
        if (orgScopeRootIds == null) {
            return null;
        }
        Set<String> union = new LinkedHashSet<>();
        for (String root : orgScopeRootIds) {
            if (root != null && !root.isBlank()) {
                union.addAll(departmentRepository.findSubtreeIds(root, departmentPathMaxDepth));
            }
        }
        return new ArrayList<>(union);
    }

    /**
     * {@code true} iff the fact's subject department is within the expanded
     * scope. DEPARTMENT subject → its own id must be in scope. EMPLOYEE subject →
     * its resolved {@code employee_proj.department} must be in scope. An
     * unresolvable subject department is NOT in a bounded scope (conservative
     * exclusion, E5).
     */
    private boolean isWithinOrgScope(ApprovalFactProjection fact, List<String> expandedScope) {
        if (expandedScope == null) {
            return true;
        }
        String subjectDepartmentId = null;
        if (fact.subjectType() == ApprovalSubjectType.DEPARTMENT) {
            subjectDepartmentId = fact.subjectId();
        } else if (fact.subjectType() == ApprovalSubjectType.EMPLOYEE && fact.subjectId() != null) {
            subjectDepartmentId = employeeRepository.findById(fact.subjectId())
                    .map(EmployeeProjection::departmentId).orElse(null);
        }
        return subjectDepartmentId != null && expandedScope.contains(subjectDepartmentId);
    }
}
