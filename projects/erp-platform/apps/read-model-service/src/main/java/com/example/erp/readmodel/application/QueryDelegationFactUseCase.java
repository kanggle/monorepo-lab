package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.query.DelegationFactPage;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactFilter;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactProjectionRepository;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Serves the read-only delegation-fact API (E5 — latest fact only; the
 * authoritative grant state + audit history stay with {@code approval-service};
 * TASK-ERP-BE-015). Unlike the approval-fact view, a delegation fact carries no
 * subject ref to resolve — the delegator/delegate are opaque employee ids exposed
 * verbatim (a console drill-down resolves names via the org-view if needed).
 *
 * <p><b>org_scope read filter (TASK-ERP-BE-008 parity, delegator-subtree).</b>
 * The list/detail are narrowed to facts whose <b>delegator's department</b> is
 * within the operator's {@code org_scope} subtree: the {@code delegatorId} is an
 * employee id resolved to its department via {@code employee_proj}, then checked
 * against the expanded subtree over {@code department_proj.parent_id}.
 * {@code orgScopeRootIds == null} (platform/{@code ["*"]}/absent) = no narrowing
 * (net-zero). A delegator whose department cannot be resolved is NOT in a bounded
 * scope (conservative exclusion, E5). Out-of-scope detail → 404 (no existence
 * leak, mirrors the approval-fact / org-view detail rule).
 */
@Service
@RequiredArgsConstructor
public class QueryDelegationFactUseCase {

    private final DelegationFactProjectionRepository delegationRepository;
    private final DepartmentProjectionRepository departmentRepository;
    private final EmployeeProjectionRepository employeeRepository;

    @Value("${erpplatform.readmodel.department-path-max-depth:32}")
    private int departmentPathMaxDepth;

    // ------------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------------

    /** Single delegation fact; 404 {@code MASTERDATA_NOT_FOUND} on a projection miss. */
    @Transactional(readOnly = true)
    public DelegationFactProjection getOne(String grantId) {
        return getOne(grantId, null);
    }

    /**
     * Single delegation fact with an optional {@code org_scope} read filter. When
     * {@code orgScopeRootIds} is non-null (a bounded operator scope) and the
     * delegator's department is NOT within the union of those subtree-roots, the
     * fact is reported as a <b>404 {@code MASTERDATA_NOT_FOUND}</b> — the
     * data-scope boundary does not leak the grant's existence. {@code null} = no
     * narrowing (net-zero).
     */
    @Transactional(readOnly = true)
    public DelegationFactProjection getOne(String grantId, List<String> orgScopeRootIds) {
        DelegationFactProjection fact = delegationRepository.findById(grantId)
                .orElseThrow(() -> new ReadModelNotFoundException(grantId));
        if (orgScopeRootIds != null
                && !isWithinOrgScope(fact, expandOrgScope(orgScopeRootIds))) {
            throw new ReadModelNotFoundException(grantId);
        }
        return fact;
    }

    // ------------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------------

    /** Paginated delegation-fact list with the explicit filters + the org_scope read filter. */
    @Transactional(readOnly = true)
    public DelegationFactPage list(String delegatorId, String delegateId,
                                   DelegationFactStatus status, Instant activeAt,
                                   List<String> orgScopeRootIds, int page, int size) {
        DelegationFactFilter filter;
        if (orgScopeRootIds == null) {
            // Net-zero: no org_scope narrowing.
            filter = DelegationFactFilter.unbounded(delegatorId, delegateId, status, activeAt);
        } else {
            List<String> scopedDepartmentIds = expandOrgScope(orgScopeRootIds);
            // Delegators in scope = employees whose department is in the subtree.
            List<String> scopedDelegatorIds =
                    employeeRepository.findIdsByDepartmentIdIn(scopedDepartmentIds);
            filter = new DelegationFactFilter(delegatorId, delegateId, status, activeAt,
                    false, scopedDelegatorIds);
        }

        List<DelegationFactProjection> facts = delegationRepository.findPage(filter, page, size);
        long total = delegationRepository.count(filter);
        return new DelegationFactPage(facts, page, size, total);
    }

    // ------------------------------------------------------------------------
    // org_scope helpers (delegator department → subtree containment)
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
     * {@code true} iff the fact's delegator's department is within the expanded
     * scope. The {@code delegatorId} is resolved to its {@code employee_proj}
     * department; an unresolvable delegator department is NOT in a bounded scope
     * (conservative exclusion, E5).
     */
    private boolean isWithinOrgScope(DelegationFactProjection fact, List<String> expandedScope) {
        if (expandedScope == null) {
            return true;
        }
        String delegatorDepartmentId = fact.delegatorId() == null ? null
                : employeeRepository.findById(fact.delegatorId())
                        .map(EmployeeProjection::departmentId).orElse(null);
        return delegatorDepartmentId != null && expandedScope.contains(delegatorDepartmentId);
    }
}
