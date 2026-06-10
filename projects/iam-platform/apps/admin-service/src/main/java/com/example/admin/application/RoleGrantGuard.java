package com.example.admin.application;

import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminRolePermissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * ADR-MONO-024 D3 (TASK-BE-347) — the single grant-menu no-escalation decision
 * site for role grants ({@code PATCH .../roles}, {@code POST /operators}).
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
 * <p>The {@code ≤-own} rule mechanically encodes the delegated-role admission
 * (ADR-024 D4-B / D5-C): {@code TENANT_ADMIN}'s permission set includes
 * {@code tenant.admin.delegate}, so only an actor holding it can grant
 * {@code TENANT_ADMIN} (in-tenant sub-delegation); only a {@code subscription.manage}
 * holder can grant {@code TENANT_BILLING_ADMIN}. The tenant confinement is the
 * separate step-1 {@link TenantScopeGuard} (this guard governs <em>which roles</em>,
 * that one governs <em>which tenant</em>).
 */
@Component
@RequiredArgsConstructor
public class RoleGrantGuard {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminGrantScopeEvaluator grantScopeEvaluator;
    private final PermissionEvaluator permissionEvaluator;
    private final AdminRolePermissionJpaRepository rolePermissions;
    private final AdminActionAuditor auditor;

    /**
     * Enforces the no-escalation grant-menu over the roles the actor is granting.
     *
     * @param actor      the granting operator
     * @param roles      the resolved roles being granted (id + name)
     * @param actionCode audit action code for a DENIED row
     * @throws RoleGrantForbiddenException when a role is outside the actor's grant menu
     */
    public void requireGrantable(OperatorContext actor,
                                 Collection<AdminOperatorPort.RoleView> roles,
                                 ActionCode actionCode) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        String actorId = actor == null ? null : actor.operatorId();

        // Platform-scope (SUPER_ADMIN '*' for operator.manage) → unconstrained menu (net-zero).
        if (grantScopeEvaluator.effectiveAdminScope(actorId, Permission.OPERATOR_MANAGE)
                .contains(com.example.admin.domain.rbac.AdminOperator.PLATFORM_TENANT_ID)) {
            return;
        }

        for (AdminOperatorPort.RoleView role : roles) {
            // (a) never grant a platform/privileged role.
            if (SUPER_ADMIN.equals(role.name())) {
                deny(actor, actionCode, role.name());
            }
            // (b) no granting more than you have — every permission of the role
            // must be held by the actor. An empty-permission role is trivially OK.
            List<String> rolePerms = rolePermissions.findPermissionKeysByRoleIds(List.of(role.id()));
            if (!rolePerms.isEmpty() && !permissionEvaluator.hasAllPermissions(actorId, rolePerms)) {
                deny(actor, actionCode, role.name());
            }
        }
    }

    private void deny(OperatorContext actor, ActionCode actionCode, String roleName) {
        auditor.recordRoleGrantForbidden(actor, actionCode, roleName);
        throw new RoleGrantForbiddenException(
                "Operator may not grant role '" + roleName
                        + "' (platform/privileged role, or exceeds the actor's own permissions)");
    }
}
