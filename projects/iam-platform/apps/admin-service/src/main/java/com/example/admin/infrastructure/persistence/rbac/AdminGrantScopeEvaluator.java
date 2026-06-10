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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminGrantScopeEvaluator {

    private final AdminOperatorJpaRepository operators;
    private final AdminOperatorRoleJpaRepository operatorRoles;
    private final AdminRolePermissionJpaRepository rolePermissions;

    /**
     * @param operatorId external operator UUID (JWT {@code sub})
     * @param permission the permission key (e.g. {@code operator.manage})
     * @return the {@code tenant_id}s of the actor's grant rows that confer
     *         {@code permission}; contains {@code '*'} for a platform grant;
     *         empty if the actor does not hold the permission or is unknown/inactive
     */
    @Transactional(readOnly = true)
    public Set<String> effectiveAdminScope(String operatorId, String permission) {
        if (operatorId == null || permission == null) {
            return Set.of();
        }
        try {
            Optional<AdminOperatorJpaEntity> op = operators.findByOperatorId(operatorId);
            if (op.isEmpty()) {
                return Set.of();
            }
            AdminOperatorJpaEntity operator = op.get();
            if (!AdminOperator.Status.ACTIVE.name().equals(operator.getStatus())) {
                return Set.of();
            }
            List<AdminOperatorRoleJpaEntity> roleRows = operatorRoles.findByOperatorId(operator.getId());
            if (roleRows.isEmpty()) {
                return Set.of();
            }
            List<Long> roleIds = roleRows.stream()
                    .map(AdminOperatorRoleJpaEntity::getRoleId)
                    .toList();
            Set<Long> grantingRoleIds = Set.copyOf(
                    rolePermissions.findRoleIdsGrantingPermission(permission, roleIds));
            if (grantingRoleIds.isEmpty()) {
                return Set.of();
            }
            Set<String> scope = new LinkedHashSet<>();
            for (AdminOperatorRoleJpaEntity row : roleRows) {
                if (grantingRoleIds.contains(row.getRoleId()) && row.getTenantId() != null) {
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
