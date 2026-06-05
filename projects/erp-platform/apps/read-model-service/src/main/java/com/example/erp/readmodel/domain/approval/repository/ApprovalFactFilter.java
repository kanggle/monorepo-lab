package com.example.erp.readmodel.domain.approval.repository;

import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;

import java.util.List;

/**
 * Query filter for the approval-fact list. Carries the optional explicit query
 * filters (status / subjectType / subjectId / approverId / submitterId) plus the
 * pre-resolved {@code org_scope} read-filter id sets.
 *
 * <p><b>org_scope semantics</b> (resolved by the query use case, applied by the
 * repository — mirrors the org-view read filter):
 * <ul>
 *   <li>{@code scopeUnbounded == true} → no narrowing (net-zero — the
 *       {@code org_scope=["*"]}/absent caller sees all facts).</li>
 *   <li>{@code scopeUnbounded == false} → only facts whose subject is in scope:
 *       a {@code DEPARTMENT} subject id ∈ {@code scopedDepartmentIds}, or an
 *       {@code EMPLOYEE} subject id ∈ {@code scopedEmployeeSubjectIds}. Both
 *       sets may be empty (zero-scope → an empty result, fail-closed).</li>
 * </ul>
 */
public record ApprovalFactFilter(
        ApprovalStatus status,
        ApprovalSubjectType subjectType,
        String subjectId,
        String approverId,
        String submitterId,
        boolean scopeUnbounded,
        List<String> scopedDepartmentIds,
        List<String> scopedEmployeeSubjectIds
) {

    public ApprovalFactFilter {
        scopedDepartmentIds = scopedDepartmentIds == null
                ? List.of() : List.copyOf(scopedDepartmentIds);
        scopedEmployeeSubjectIds = scopedEmployeeSubjectIds == null
                ? List.of() : List.copyOf(scopedEmployeeSubjectIds);
    }

    /** A net-zero (platform / unbounded) filter carrying only the explicit query filters. */
    public static ApprovalFactFilter unbounded(ApprovalStatus status,
                                               ApprovalSubjectType subjectType,
                                               String subjectId, String approverId,
                                               String submitterId) {
        return new ApprovalFactFilter(status, subjectType, subjectId, approverId, submitterId,
                true, List.of(), List.of());
    }
}
