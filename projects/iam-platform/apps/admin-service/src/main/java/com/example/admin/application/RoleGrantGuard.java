package com.example.admin.application;

import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminRolePermissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * ADR-MONO-024 D3 (TASK-BE-347) — the single grant-menu no-escalation decision
 * site for role grants ({@code PATCH .../roles}, {@code POST /operators}, invitations, groups, org-node grants).
 *
 * <p>Rule:
 * <ul>
 *   <li><b>Platform-scope actor</b> ({@code '*' ∈ effectiveAdminScope(actor, operator.manage)},
 *       i.e. SUPER_ADMIN) → <b>unconstrained</b> (net-zero — platform onboarding unchanged).</li>
 *   <li><b>Non-platform actor</b> — for each role being granted, deny
 *       ({@code 403 ROLE_GRANT_FORBIDDEN}, audited) if the role is {@code SUPER_ADMIN}
 *       (platform/privileged role) OR the actor does not hold ALL of the role's
 *       permissions (no granting more than you have).</li>
 * </ul>
 *
 * <p><b>TASK-MONO-784 S3 (ADR-MONO-082 b · decision D-6).</b> «What you have» is measured <b>in the tenant the
 * granted role row will live in</b> ({@code tenantId}): the permissions of the actor's role rows there — that
 * tenant's rows, {@code '*'} rows, node rows whose subtree covers it
 * ({@link AdminGrantScopeEvaluator#permissionsHeldIn}). Before S3 it was the permission union over every role
 * row the actor holds, so a {@code TENANT_ADMIN} of company A could grant {@code TENANT_ADMIN} in company B
 * where it held only a support role (784 AC-0 F3). For a single-company actor the two are the same set.
 *
 * <p>The tenant confinement itself is the separate {@link TenantScopeGuard} / {@link QueryTenantScopeGate}
 * (this guard governs <em>which roles</em>, those govern <em>which tenant</em>).
 */
@Component
@RequiredArgsConstructor
public class RoleGrantGuard {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final AdminRolePermissionJpaRepository rolePermissions;
    private final AdminActionAuditor auditor;

    /**
     * Enforces the no-escalation grant-menu over the roles the actor is granting.
     *
     * @param actor      the granting operator
     * @param roles      the resolved roles being granted (id + name)
     * @param actionCode audit action code for a DENIED row
     * @param tenantId   the tenant the granted role rows live in (784 D-6)
     * @throws RoleGrantForbiddenException when a role is outside the actor's grant menu there
     */
    public void requireGrantable(OperatorContext actor,
                                 Collection<AdminOperatorPort.RoleView> roles,
                                 ActionCode actionCode,
                                 String tenantId) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        String actorId = actor == null ? null : actor.operatorId();
        // Platform-scope (SUPER_ADMIN '*' for operator.manage) → unconstrained menu (net-zero).
        if (isPlatformScope(actorId)) {
            return;
        }
        Set<String> held = grantScopeEvaluator.permissionsHeldIn(actorId, tenantId);
        for (AdminOperatorPort.RoleView role : roles) {
            // Same rule as the read mirror (isGrantable); on deny, audit + throw.
            if (!isGrantable(held, role)) {
                deny(actor, actionCode, role.name());
            }
        }
    }

    /**
     * ADR-MONO-024 D3 read mirror (TASK-BE-388) — the subset of {@code allRoles} the actor may grant in
     * {@code tenantId}, by name, in input order. No audit rows, no throw. Shares {@link #isGrantable} with
     * {@link #requireGrantable} so the read hint can never drift from the enforcement.
     */
    public List<String> grantableRoleNames(OperatorContext actor,
                                            Collection<AdminOperatorPort.RoleView> allRoles,
                                            String tenantId) {
        if (allRoles == null || allRoles.isEmpty()) {
            return List.of();
        }
        String actorId = actor == null ? null : actor.operatorId();
        boolean actorIsPlatform = isPlatformScope(actorId);
        Set<String> held = actorIsPlatform ? Set.of() : grantScopeEvaluator.permissionsHeldIn(actorId, tenantId);

        List<String> out = new ArrayList<>(allRoles.size());
        for (AdminOperatorPort.RoleView role : allRoles) {
            if (actorIsPlatform || isGrantable(held, role)) {
                out.add(role.name());
            }
        }
        return out;
    }

    /** {@code '*' ∈ effectiveAdminScope(actor, operator.manage)} (SUPER_ADMIN). */
    private boolean isPlatformScope(String actorId) {
        return grantScopeEvaluator.effectiveAdminScope(actorId, Permission.OPERATOR_MANAGE)
                .contains(AdminOperator.PLATFORM_TENANT_ID);
    }

    /**
     * Non-platform grantability: never {@code SUPER_ADMIN}; every permission of the role must be in
     * {@code held} (≤-own in the tenant). An empty-permission role is trivially grantable.
     */
    private boolean isGrantable(Set<String> held, AdminOperatorPort.RoleView role) {
        if (SUPER_ADMIN.equals(role.name())) {
            return false;
        }
        List<String> rolePerms = rolePermissions.findPermissionKeysByRoleIds(List.of(role.id()));
        return rolePerms.isEmpty() || held.containsAll(rolePerms);
    }

    private void deny(OperatorContext actor, ActionCode actionCode, String roleName) {
        auditor.recordRoleGrantForbidden(actor, actionCode, roleName);
        throw new RoleGrantForbiddenException(
                "Operator may not grant role '" + roleName
                        + "' (platform/privileged role, or exceeds the actor's own permissions)");
    }
}
