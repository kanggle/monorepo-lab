package com.example.admin.application.port;

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
}
