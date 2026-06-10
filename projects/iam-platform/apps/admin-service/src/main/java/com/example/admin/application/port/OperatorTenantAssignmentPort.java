package com.example.admin.application.port;

import java.util.List;
import java.util.Optional;
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

    /**
     * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — the single assignment
     * row for a specific (operator, tenant), projected for the admin-facing
     * org_scope management surface ({@code GET .../assignments}).
     *
     * @param operatorInternalId internal BIGINT surrogate id
     *                           ({@code admin_operators.id}); may be {@code null}
     * @param tenantId           the assigned (active) customer tenant id
     * @return the assignment projection, or {@link Optional#empty()} when no
     *         explicit assignment row exists for the (operator, tenant)
     */
    Optional<AssignmentView> findAssignment(Long operatorInternalId, String tenantId);

    /**
     * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — set/clear the
     * per-assignment {@code org_scope} on the (operator, tenant) row.
     *
     * <p>Persisted with an explicit {@code saveAndFlush} (BE-335 lesson: a dirty
     * UPDATE must be flushed, never left to commit-time auto-flush which can be
     * silently lost). The caller MUST have verified the row exists
     * ({@link #findAssignment}) — this method does not create rows.
     *
     * <p>Value semantics: {@code null} clears the column (⟺ {@code ["*"]}
     * net-zero whole tenant); an empty list {@code []} persists an explicit
     * zero-scope (distinct from {@code null}); a non-empty list persists the
     * verbatim (already-normalized) subtree-root ids.
     *
     * @param operatorInternalId internal BIGINT surrogate id
     *                           ({@code admin_operators.id})
     * @param tenantId           the assigned (active) customer tenant id
     * @param orgScope           the new org_scope value, or {@code null} to clear
     */
    void updateOrgScope(Long operatorInternalId, String tenantId, List<String> orgScope);

    /**
     * TASK-BE-347 (ADR-MONO-024 D3-i) — whether an explicit assignment row exists
     * for the (operator, tenant) pair.
     */
    boolean assignmentExists(Long operatorInternalId, String tenantId);

    /**
     * TASK-BE-347 (ADR-MONO-024 D3-i) — create a whole-tenant assignment row
     * (operator ↔ tenant): {@code org_scope=null} (⟺ {@code ["*"]}),
     * {@code permission_set_id=null} (inherit operator-level roles). The caller MUST
     * have verified the row does not already exist ({@link #assignmentExists}).
     *
     * @param operatorInternalId internal BIGINT id of the assigned operator
     * @param tenantId           the ASSIGNED tenant
     * @param grantedBy          internal BIGINT id of the granting operator, or {@code null}
     */
    void createAssignment(Long operatorInternalId, String tenantId, Long grantedBy);

    /**
     * TASK-BE-347 (ADR-MONO-024 D3-i) — remove the assignment row for the
     * (operator, tenant) pair. No-op if it does not exist (the caller's
     * {@code ASSIGNMENT_NOT_FOUND} gate is authoritative).
     */
    void deleteAssignment(Long operatorInternalId, String tenantId);

    /**
     * Immutable projection of an {@code operator_tenant_assignment} row for the
     * admin-facing management surface. {@code orgScope} is {@code null} when the
     * column is unset (⟺ {@code ["*"]} net-zero) and is rendered ABSENT in JSON
     * by the controller's {@code @JsonInclude(NON_NULL)}.
     */
    record AssignmentView(
            String tenantId,
            List<String> orgScope,
            Long permissionSetId
    ) {}
}
