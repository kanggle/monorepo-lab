package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The single tenant gate for the endpoints whose <em>acting tenant</em> («행동 테넌트») comes from the
 * request ({@code tenantId} query / {@code X-Tenant-Id} header): the audit · accounts · operators ·
 * operator-invitation reads, the operator e-mail lookup, the grant-menu read, and the account / session
 * <em>writes</em> (lock · unlock · bulk-lock · GDPR delete · export · session revoke) that reuse it
 * (TASK-BE-357 / BE-467 lineage).
 *
 * <p><b>TASK-MONO-784 S3 (ADR-MONO-082 b · decision D-4 · admin-api.md § 행동 테넌트).</b> This is the second
 * of the two stages: {@code @RequiresPermission} has already said «this operator holds the permission
 * <em>somewhere</em>»; this gate says <em>where</em>. The tenant must be in the permission's
 * <b>grant scope</b> — {@link AdminGrantScopeEvaluator#effectiveAdminScope}, the tenants of the role rows
 * that confer that permission — never the memberships and never the home. Before S3 it was the effective
 * (operational) scope, home ∪ assignments, which let a role held in company A answer a read — or a lock —
 * in company B where the operator was merely assigned (784 AC-1 · AC-0 F2). A read with no permission key
 * uses {@link AdminGrantScopeEvaluator#anyRoleScope} instead (rider R2: a membership with no role row
 * reaches nothing).
 *
 * <p><b>Omitted tenant</b> (neither «home» — R1 — nor «the union of memberships» — inheritance):
 * <ul>
 *   <li>grant scope contains {@code '*'} → {@code '*'} (what the platform home used to give);</li>
 *   <li>exactly one concrete tenant → that tenant (byte-identical to the old home default for every
 *       single-company operator, whose role rows live at the home — V0026);</li>
 *   <li>none → {@code 403 TENANT_SCOPE_DENIED};</li>
 *   <li>two or more → {@code 400 VALIDATION_ERROR} («tenantId is required»).</li>
 * </ul>
 *
 * <p>{@code isPlatformScope} is now «the grant scope contains {@code '*'}» — the role, not the home
 * column — so a consumer that stamps {@code '*'} downstream for a platform admin (lock · GDPR, TASK-BE-619 /
 * BE-621) keys on the same {@code '*'} grant row it always had.
 *
 * <p>Distinct from {@link TenantScopeGuard}, whose target tenant is a resource's tenant (an invitation's,
 * a group's, a managed operator's) rather than a caller-chosen one. Both read the same grant scope.
 */
@Component
@RequiredArgsConstructor
public class QueryTenantScopeGate {

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final AdminActionAuditor auditor;

    /**
     * The resolved acting tenant plus whether the caller's grant scope is platform-wide ({@code '*'}).
     * Consumers branch on {@code isPlatformScope} when a cross-tenant ({@code "*"}) finder differs from the
     * single-tenant finder.
     */
    public record Resolved(String tenantId, boolean isPlatformScope) {}

    /**
     * Resolves + gates the acting tenant against the grant scope of {@code permission}; an out-of-scope
     * tenant writes a best-effort DENIED {@code admin_actions} row (TASK-BE-262) before the 403.
     *
     * @param operator          the authenticated operator
     * @param requestedTenantId the requested tenant; {@code null}/blank → the omitted-tenant rule
     * @param actionCode        the audit action code for a DENIED row
     * @param permission        the permission whose grant scope decides
     * @throws TenantScopeDeniedException out of scope, or omitted with an empty scope
     * @throws IllegalArgumentException   omitted while the scope names two or more tenants (400)
     */
    public Resolved resolve(OperatorContext operator,
                            String requestedTenantId,
                            ActionCode actionCode,
                            String permission) {
        return decide(operator, requestedTenantId, grantScope(operator, permission), actionCode, permission);
    }

    /**
     * {@link #resolve} without the DENIED row — for a read whose contract writes no audit row on refusal
     * ({@code GET /api/admin/operators}, {@code GET /api/admin/operator-invitations},
     * {@code GET /api/admin/operators/grantable-roles}; BE-486 read-path convention).
     */
    public Resolved resolveWithoutDenyRow(OperatorContext operator, String requestedTenantId, String permission) {
        return decide(operator, requestedTenantId, grantScope(operator, permission), null, permission);
    }

    /**
     * The gate for a read with <b>no permission key</b> ({@code GET /api/admin/accounts?email=},
     * {@code GET /api/admin/operators/lookup}): the scope is every tenant where the operator holds any role
     * row (R2). Writes the DENIED row like {@link #resolve}; {@code citedPermission} is informational only.
     */
    public Resolved resolveAnyRole(OperatorContext operator,
                                   String requestedTenantId,
                                   ActionCode actionCode,
                                   String citedPermission) {
        Set<String> scope = grantScopeEvaluator.anyRoleScope(operatorId(operator));
        return decide(operator, requestedTenantId, scope, actionCode, citedPermission);
    }

    private Set<String> grantScope(OperatorContext operator, String permission) {
        return grantScopeEvaluator.effectiveAdminScope(operatorId(operator), permission);
    }

    private Resolved decide(OperatorContext operator,
                            String requestedTenantId,
                            Set<String> scope,
                            ActionCode actionCodeOrNullForNoRow,
                            String permission) {
        boolean isPlatformScope = scope.contains(AdminOperator.PLATFORM_TENANT_ID);
        boolean omitted = requestedTenantId == null || requestedTenantId.isBlank();

        if (!omitted) {
            if (isPlatformScope || scope.contains(requestedTenantId)) {
                return new Resolved(requestedTenantId, isPlatformScope);
            }
            throw deny(operator, actionCodeOrNullForNoRow, permission, requestedTenantId);
        }

        if (isPlatformScope) {
            return new Resolved(AdminOperator.PLATFORM_TENANT_ID, true);
        }
        if (scope.size() == 1) {
            return new Resolved(scope.iterator().next(), false);
        }
        if (scope.isEmpty()) {
            throw deny(operator, actionCodeOrNullForNoRow, permission, null);
        }
        throw new IllegalArgumentException(
                "tenantId is required: permission '" + permission + "' is held in more than one tenant");
    }

    private TenantScopeDeniedException deny(OperatorContext operator,
                                            ActionCode actionCode,
                                            String permission,
                                            String attemptedTenantId) {
        if (actionCode != null && operator != null) {
            // TASK-BE-262: best-effort DENIED audit row before throwing (home tenant — DENIED rows never
            // name the attempted tenant as their own, 784 D-13).
            auditor.recordCrossTenantDenied(operator, null, actionCode, permission, attemptedTenantId);
        }
        return new TenantScopeDeniedException(attemptedTenantId == null
                ? "Operator holds '" + permission + "' in no tenant"
                : "Operator is not scoped to tenant '" + attemptedTenantId + "' for permission '" + permission + "'");
    }

    private static String operatorId(OperatorContext operator) {
        return operator == null ? null : operator.operatorId();
    }
}
