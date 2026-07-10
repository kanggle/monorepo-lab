package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.domain.rbac.AdminOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ADR-MONO-024 D2 — the operator's <em>effective admin-grant scope</em> for a
 * permission: the set of {@code tenant_id}s of the {@code admin_operator_roles}
 * rows that grant that permission. {@code '*'} = platform-all.
 *
 * <p><b>Distinct from the operational (assume-tenant) scope.</b>
 * {@code TenantScopeResolver} (BE-326) resolves which tenants an operator may
 * <em>operate</em> in (home ∪ {@code operator_tenant_assignment}); that gates the
 * assume-tenant domain token. THIS resolver answers a different question — which
 * tenants the operator may <em>administer</em> (manage operators / subscriptions)
 * for a given permission — and reads only the role-grant tenant scope. An
 * operator assigned to operate {acme, globex} but granted {@code operator.manage}
 * only @ acme must be denied administering globex; only the grant scope, never the
 * assignment scope, may be consulted here.
 *
 * <p><b>NET-ZERO.</b> The only seeded holder of {@code operator.manage} /
 * {@code subscription.manage} today is {@code SUPER_ADMIN}, whose grant rows carry
 * {@code tenant_id='*'}. So {@code effectiveAdminScope(superAdmin, …) == {"*"}} and
 * {@link #isTenantInAdminScope} returns {@code true} for every target — the gate
 * denies nobody until ADR-024 step 2 seeds a non-platform admin role.
 *
 * <p>Fail-closed: any unexpected error during resolution yields an empty scope
 * (deny), mirroring {@link PermissionEvaluatorImpl}.
 *
 * <p><b>ADR-MONO-047 D5 amendment (TASK-BE-492).</b> A grant row may now carry a
 * nullable {@code org_node_id} (V0042). Each granting row resolves in this order:
 * <ol>
 *   <li>{@code tenant_id = '*'} → platform. Evaluated as a <b>PRE-SCAN over every
 *       granting row, before any subtree round-trip</b>. It is deliberately NOT an
 *       in-loop short-circuit: row iteration order would then decide whether
 *       {@code subtreeTenantIds()} is called before the {@code '*'} row is reached, and
 *       an account-service outage would make a {@code SUPER_ADMIN} <em>lose</em> platform
 *       reach — fail-closed cutting down the one principal it must never cut down.</li>
 *   <li>{@code org_node_id IS NOT NULL} → the tenants of that node's subtree, resolved
 *       fail-closed ({@link OrgNodeSubtreeResolver}). An unresolvable subtree contributes
 *       the EMPTY set — never {@code '*'}, never all tenants.</li>
 *   <li>otherwise → {@code {tenant_id}} (byte-unchanged legacy behaviour).</li>
 * </ol>
 *
 * <p>NET-ZERO until the first {@code ORG_ADMIN @ node} grant exists: V0041 seeds the role
 * to nobody, so no row has a non-null {@code org_node_id} and branch (2) is unreachable.
 *
 * <p>The subtree round-trip happens inside this read-only transaction. That is bounded by
 * the org-node client's short connect/read timeouts (no retry) plus a brief success-only
 * cache, so a hung authority times the permission check out CLOSED rather than pinning a
 * connection for the default 10s downstream window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminGrantScopeEvaluator {

    private final AdminOperatorJpaRepository operators;
    private final AdminOperatorRoleJpaRepository operatorRoles;
    private final AdminRolePermissionJpaRepository rolePermissions;
    private final OrgNodeSubtreeResolver subtreeResolver;

    /**
     * @param operatorId external operator UUID (JWT {@code sub})
     * @param permission the permission key (e.g. {@code operator.manage})
     * @return the {@code tenant_id}s of the actor's grant rows that confer
     *         {@code permission}; exactly {@code {'*'}} for a platform grant;
     *         a node-scoped grant contributes its subtree's tenants;
     *         empty if the actor does not hold the permission or is unknown/inactive
     */
    @Transactional(readOnly = true)
    public Set<String> effectiveAdminScope(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return Set.of();
        }
        try {
            List<AdminOperatorRoleJpaEntity> grantingRows = grantingRows(operatorId, permission);
            if (grantingRows.isEmpty()) {
                return Set.of();
            }

            // (1) Platform pre-scan — FIRST, before any subtree round-trip. Order-independent.
            for (AdminOperatorRoleJpaEntity row : grantingRows) {
                if (AdminOperator.PLATFORM_TENANT_ID.equals(row.getTenantId())) {
                    return Set.of(AdminOperator.PLATFORM_TENANT_ID);
                }
            }

            Set<String> scope = new LinkedHashSet<>();
            for (AdminOperatorRoleJpaEntity row : grantingRows) {
                if (row.getOrgNodeId() != null) {
                    // (2) org-node-scoped grant (ORG_ADMIN) — subtree driver. Fail-closed:
                    // an unresolvable subtree contributes nothing.
                    scope.addAll(subtreeResolver.subtreeTenantIdsFailClosed(row.getOrgNodeId()));
                } else if (row.getTenantId() != null) {
                    // (3) tenant-scoped grant (TENANT_ADMIN) — byte-unchanged.
                    scope.add(row.getTenantId());
                }
            }
            return Set.copyOf(scope);
        } catch (RuntimeException ex) {
            log.error("Admin-grant scope resolution failed (fail-closed) operatorId={} permission={}",
                    operatorId, permission, ex);
            return Set.of();
        }
    }

    /**
     * TASK-BE-492 (ADR-MONO-047 D5) — the org-node ids at which the actor holds a
     * node-scoped grant conferring {@code permission}. Pure DB: no account-service
     * round-trip, so the org-node reach predicates
     * ({@code administers} / {@code strictlyAdministers}) can be evaluated without
     * expanding any subtree.
     *
     * <p>Empty for a platform actor — {@code SUPER_ADMIN}'s reach comes from {@code '*'}
     * (see {@link #isPlatformScope}), never from a node.
     */
    @Transactional(readOnly = true)
    public Set<String> grantedOrgNodeIds(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return Set.of();
        }
        try {
            Set<String> nodeIds = new LinkedHashSet<>();
            for (AdminOperatorRoleJpaEntity row : grantingRows(operatorId, permission)) {
                if (row.getOrgNodeId() != null) {
                    nodeIds.add(row.getOrgNodeId());
                }
            }
            return Set.copyOf(nodeIds);
        } catch (RuntimeException ex) {
            log.error("Org-node grant resolution failed (fail-closed) operatorId={} permission={}",
                    operatorId, permission, ex);
            return Set.of();
        }
    }

    /**
     * TASK-BE-492 — {@code true} iff the actor holds {@code permission} via a platform grant
     * ({@code tenant_id='*'}). Never issues a subtree round-trip, so it stays true while
     * account-service is down.
     */
    @Transactional(readOnly = true)
    public boolean isPlatformScope(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return false;
        }
        try {
            for (AdminOperatorRoleJpaEntity row : grantingRows(operatorId, permission)) {
                if (AdminOperator.PLATFORM_TENANT_ID.equals(row.getTenantId())) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            log.error("Platform-scope resolution failed (fail-closed) operatorId={} permission={}",
                    operatorId, permission, ex);
            return false;
        }
    }

    /**
     * The ACTIVE actor's {@code admin_operator_roles} rows whose role confers
     * {@code permission}. Empty when the operator is unknown, inactive, roleless, or holds
     * no granting role.
     */
    private List<AdminOperatorRoleJpaEntity> grantingRows(String operatorId, String permission) {
        Optional<AdminOperatorJpaEntity> op = operators.findByOperatorId(operatorId);
        if (op.isEmpty()) {
            return List.of();
        }
        AdminOperatorJpaEntity operator = op.get();
        if (!AdminOperator.Status.ACTIVE.name().equals(operator.getStatus())) {
            return List.of();
        }
        List<AdminOperatorRoleJpaEntity> roleRows = operatorRoles.findByOperatorId(operator.getId());
        if (roleRows.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roleRows.stream()
                .map(AdminOperatorRoleJpaEntity::getRoleId)
                .toList();
        Set<Long> grantingRoleIds = Set.copyOf(
                rolePermissions.findRoleIdsGrantingPermission(permission, roleIds));
        if (grantingRoleIds.isEmpty()) {
            return List.of();
        }
        return roleRows.stream()
                .filter(row -> grantingRoleIds.contains(row.getRoleId()))
                .toList();
    }

    /**
     * ADR-MONO-024 D2 central confinement predicate. {@code true} iff the actor's
     * effective admin-grant scope for {@code permission} contains the platform
     * sentinel {@code '*'} (net-zero for SUPER_ADMIN) or {@code targetTenantId}.
     *
     * @param targetTenantId the tenant the mutation targets; {@code null} → deny
     *                       (fail-closed — every gated mutation resolves a concrete target)
     */
    public boolean isTenantInAdminScope(String operatorId, String permission, String targetTenantId) {
        Set<String> scope = effectiveAdminScope(operatorId, permission);
        if (scope.contains(AdminOperator.PLATFORM_TENANT_ID)) {
            return true;
        }
        return targetTenantId != null && scope.contains(targetTenantId);
    }
}
