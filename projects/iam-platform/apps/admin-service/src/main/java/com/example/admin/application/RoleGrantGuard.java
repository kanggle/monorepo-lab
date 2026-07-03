package com.example.admin.application;

import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminRolePermissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        boolean actorIsPlatform = isPlatformScope(actorId);

        // Platform-scope (SUPER_ADMIN '*' for operator.manage) → unconstrained menu (net-zero).
        if (actorIsPlatform) {
            return;
        }

        for (AdminOperatorPort.RoleView role : roles) {
            // Same rule as the read mirror (isGrantable); on deny, audit + throw
            // (the write-path enforcement side — the read side never audits).
            if (!isGrantable(actorId, false, role)) {
                deny(actor, actionCode, role.name());
            }
        }
    }

    /**
     * ADR-MONO-024 D3 read mirror (TASK-BE-388) — the pure, side-effect-free
     * projection of {@link #requireGrantable}: the subset of {@code allRoles} the
     * actor may grant, by name, in the roles' input order. No audit rows, no throw
     * — a read hint for the operator create / role-edit forms so they never expose
     * a role the producer {@code RoleGrantGuard} (403 {@code ROLE_GRANT_FORBIDDEN})
     * would ultimately reject. Shares the exact grantability predicate
     * ({@link #isGrantable}) so the read hint can never drift from the enforcement.
     *
     * @param actor    the operator whose grant menu is being computed
     * @param allRoles the full seed-role set (stable id order recommended)
     * @return the grantable role names, preserving {@code allRoles} iteration order
     */
    public List<String> grantableRoleNames(OperatorContext actor,
                                            Collection<AdminOperatorPort.RoleView> allRoles) {
        if (allRoles == null || allRoles.isEmpty()) {
            return List.of();
        }
        String actorId = actor == null ? null : actor.operatorId();
        boolean actorIsPlatform = isPlatformScope(actorId);

        List<String> out = new ArrayList<>(allRoles.size());
        for (AdminOperatorPort.RoleView role : allRoles) {
            if (isGrantable(actorId, actorIsPlatform, role)) {
                out.add(role.name());
            }
        }
        return out;
    }

    /**
     * Platform-scope predicate shared by both the write gate and the read mirror:
     * {@code '*' ∈ effectiveAdminScope(actor, operator.manage)} (SUPER_ADMIN).
     */
    private boolean isPlatformScope(String actorId) {
        return grantScopeEvaluator.effectiveAdminScope(actorId, Permission.OPERATOR_MANAGE)
                .contains(AdminOperator.PLATFORM_TENANT_ID);
    }

    /**
     * The single grantability predicate (no side effects) shared by
     * {@link #requireGrantable} (enforce) and {@link #grantableRoleNames} (hint):
     * <ul>
     *   <li>platform-scope actor → every role is grantable (net-zero);</li>
     *   <li>otherwise: never {@code SUPER_ADMIN} (platform/privileged role), and
     *       every permission of the role must be held by the actor (≤-own). An
     *       empty-permission role is trivially grantable.</li>
     * </ul>
     */
    private boolean isGrantable(String actorId, boolean actorIsPlatform, AdminOperatorPort.RoleView role) {
        if (actorIsPlatform) {
            return true;
        }
        // (a) never grant a platform/privileged role.
        if (SUPER_ADMIN.equals(role.name())) {
            return false;
        }
        // (b) no granting more than you have — every permission of the role must be
        // held by the actor. An empty-permission role is trivially grantable.
        List<String> rolePerms = rolePermissions.findPermissionKeysByRoleIds(List.of(role.id()));
        return rolePerms.isEmpty() || permissionEvaluator.hasAllPermissions(actorId, rolePerms);
    }

    private void deny(OperatorContext actor, ActionCode actionCode, String roleName) {
        auditor.recordRoleGrantForbidden(actor, actionCode, roleName);
        throw new RoleGrantForbiddenException(
                "Operator may not grant role '" + roleName
                        + "' (platform/privileged role, or exceeds the actor's own permissions)");
    }
}
