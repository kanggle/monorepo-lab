package com.example.admin.application;

import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * TASK-BE-326 / ADR-MONO-020 D1 + D6 step 1 — the single core for an operator's <em>effective tenant
 * scope</em> (its <b>memberships</b>).
 *
 * <p><b>TASK-MONO-784 S2 (ADR-MONO-082 b · rider R1 · D3 · decision D-2):</b> effective scope =
 * the operator's {@code operator_tenant_assignment} rows <b>only</b>. The home tenant
 * ({@code admin_operators.tenant_id}) is no longer added — it stays on the row as «the first company
 * the operator belonged to» (R1) but no decision reads it. Before 784 this was the BE-326 dual-read
 * {@code assignments ∪ {home}}; keeping the home in the union would let an operator removed from the
 * company that is their home still assume into it (784 AC-0 F4, AC-2).
 *
 * <p>No single-company operator changes answer: V0051 backfilled a whole-tenant assignment row for
 * every non-{@code '*'} operator's home (and role-row tenants), and every operator-creating path writes
 * one (invitation acceptance, self-service onboarding). Fixtures and seeds that create an operator row
 * by hand must add the assignment row too.
 *
 * <p>A platform-scope operator ({@code tenant_id == '*'}) still resolves to the unchanged sentinel
 * {@code {"*"}} — assignments are NOT read for it (ADR-082 D2: {@code '*'} is not a membership).
 *
 * <p>Consumers: the assume-tenant assignment check, the console registry, and (until S3 retargets them at
 * the grant scope) the read gate {@code QueryTenantScopeGate} and its copies.
 */
@Service
@RequiredArgsConstructor
public class TenantScopeResolver {

    private final OperatorTenantAssignmentPort assignmentPort;

    /**
     * Resolves the effective tenant scope (the operator's memberships).
     *
     * @param operatorInternalId internal BIGINT id ({@code admin_operators.id})
     * @param homeTenantId       the operator's home tenant ({@code admin_operators.tenant_id}) — read
     *                           ONLY to recognise the {@code '*'} sentinel; a concrete home is not
     *                           part of the result (R1)
     * @return immutable effective scope set. {@code {"*"}} when the home is the platform sentinel;
     *         otherwise exactly the assigned tenantIds (empty when the operator has no membership)
     */
    public Set<String> resolveEffectiveTenantScope(Long operatorInternalId, String homeTenantId) {
        // Platform-scope sentinel: never expanded by assignments.
        if (AdminOperator.PLATFORM_TENANT_ID.equals(homeTenantId)) {
            return Set.of(AdminOperator.PLATFORM_TENANT_ID);
        }
        return Set.copyOf(assignmentPort.findAssignedTenantIds(operatorInternalId));
    }
}
