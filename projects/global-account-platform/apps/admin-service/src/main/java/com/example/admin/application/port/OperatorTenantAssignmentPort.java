package com.example.admin.application.port;

import java.util.List;
import java.util.Set;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 — application-layer port over the
 * {@code operator_tenant_assignment} table. Keeps the JPA entity / repository
 * out of the application layer's import graph (architecture.md Allowed
 * Dependencies — same pattern as {@link OperatorLookupPort} /
 * {@link AdminOperatorPort}).
 *
 * <p>Consumed by {@code com.example.admin.application.TenantScopeResolver} to
 * compute an operator's effective tenant scope (assignment tenantIds ∪
 * {legacy home tenantId}).
 */
public interface OperatorTenantAssignmentPort {

    /**
     * @param operatorInternalId internal BIGINT surrogate id
     *                           ({@code admin_operators.id})
     * @return the set of ASSIGNED tenantIds for the operator (never {@code null};
     *         empty when the operator has no assignment rows — the net-zero case).
     */
    Set<String> findAssignedTenantIds(Long operatorInternalId);

    /**
     * TASK-BE-338 / ADR-MONO-020 D3 amendment — the per-assignment data-scope
     * ({@code org_scope}) for a specific (operator, selected tenant).
     *
     * <p>Returns the assignment row's {@code org_scope} JSON array (department
     * subtree-root ids). {@code null} ⟺ {@code ["*"]} = whole tenant (net-zero):
     * returned for an unset column AND for the absence of an explicit assignment
     * row (legacy home-tenant resolution / platform-scope). An empty list
     * ({@code []}) is an explicit zero-scope and is returned verbatim (NOT widened
     * to {@code ["*"]}).
     *
     * @param operatorInternalId internal BIGINT surrogate id
     *                           ({@code admin_operators.id}); may be {@code null}
     * @param tenantId           the selected (assume-target) customer tenant id
     * @return the assignment's {@code org_scope} list, or {@code null} when unset /
     *         no explicit row (caller defaults to {@code ["*"]})
     */
    List<String> findOrgScope(Long operatorInternalId, String tenantId);
}
