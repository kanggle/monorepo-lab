package com.example.admin.domain.rbac;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 + D5 — a single operator↔tenant assignment row.
 *
 * <p>Models a tenant an operator has been GRANTED scope over, beyond its single
 * home tenant ({@code admin_operators.tenant_id}). The effective tenant scope of
 * an operator is the union of these assignment {@link #tenantId() tenantIds} and
 * its legacy home tenant (see
 * {@code com.example.admin.application.TenantScopeResolver}).
 *
 * <p>Framework-free domain POJO (no JPA / Spring annotations).
 *
 * @param operatorId      internal BIGINT surrogate id of the assigned operator
 *                        ({@code admin_operators.id}) — NOT the external
 *                        {@code operator_id} UUID.
 * @param tenantId        the ASSIGNED tenant (a tenant the operator may act
 *                        within) — distinct from the operator's home tenant.
 * @param permissionSetId D5 per-assignment permission-set ({@code admin_roles.id}),
 *                        or {@code null} to inherit the operator-level role grants.
 * @param orgScope        TASK-BE-338 / ADR-MONO-020 D3 amendment — per-assignment
 *                        data-scope (department subtree-root ids the operator may
 *                        act under within this assigned tenant). {@code null} ⟺
 *                        {@code ["*"]} = whole tenant (net-zero); {@code []} =
 *                        explicit zero-scope. Sibling of {@code permissionSetId}.
 * @param grantedAt       when the assignment was granted.
 * @param grantedBy       internal BIGINT surrogate id of the granting operator,
 *                        or {@code null}.
 */
public record OperatorTenantAssignment(
        Long operatorId,
        String tenantId,
        Long permissionSetId,
        List<String> orgScope,
        Instant grantedAt,
        Long grantedBy
) {
}
