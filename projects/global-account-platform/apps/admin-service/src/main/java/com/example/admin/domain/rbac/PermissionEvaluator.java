package com.example.admin.domain.rbac;

import java.util.Collection;

/**
 * Port for runtime permission evaluation. See specs/services/admin-service/rbac.md
 * "Permission Evaluation Algorithm".
 *
 * <p>Returns {@code false} for any of: unknown operator, inactive operator,
 * or missing permission binding. Infrastructure adapter implementations must
 * fail-closed (treat DB errors as deny).
 *
 * <p>TASK-BE-249: {@link #isTenantAllowed(AdminOperator, String)} added for
 * tenant-scope enforcement. The 2-arg {@code hasPermission} / {@code hasAllPermissions}
 * methods remain for self-scoped actions (where operator is acting within their
 * own tenant); cross-tenant gate is enforced via {@link #isTenantAllowed}.
 */
public interface PermissionEvaluator {

    /** @return true if the operator holds {@code permission} via any of its roles. */
    boolean hasPermission(String operatorId, String permission);

    /** @return true if the operator holds every permission in {@code permissions}. */
    boolean hasAllPermissions(String operatorId, Collection<String> permissions);

    /**
     * Evaluates whether {@code operator} may act against {@code targetTenantId}.
     *
     * <ul>
     *   <li>If {@code operator.isPlatformScope()} → {@code true} (subject to
     *       action-level permission check via {@link #hasPermission}).</li>
     *   <li>Else if {@code operator.tenantId().equals(targetTenantId)} → {@code true}.</li>
     *   <li>Otherwise → {@code false} (TENANT_SCOPE_DENIED).</li>
     * </ul>
     *
     * <p>When {@code targetTenantId} is {@code null} the method defaults to the
     * operator's own tenant (legacy single-tenant compat — spec §Edge Cases).
     *
     * @param operator       the authenticated operator
     * @param targetTenantId the tenant the action targets; {@code null} means
     *                       "same as operator's tenant"
     * @return {@code true} if the operator may act in {@code targetTenantId}
     */
    default boolean isTenantAllowed(AdminOperator operator, String targetTenantId) {
        if (operator == null) {
            return false;
        }
        // Platform-scope operators (SUPER_ADMIN) are always allowed.
        if (operator.isPlatformScope()) {
            return true;
        }
        // Null targetTenantId → default to operator's own tenant (legacy compat).
        String resolved = (targetTenantId == null) ? operator.tenantId() : targetTenantId;
        return operator.tenantId() != null && operator.tenantId().equals(resolved);
    }
}
