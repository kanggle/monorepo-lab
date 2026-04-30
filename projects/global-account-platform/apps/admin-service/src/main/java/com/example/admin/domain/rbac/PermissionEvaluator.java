package com.example.admin.domain.rbac;

import java.util.Collection;

/**
 * Port for runtime permission evaluation. See specs/services/admin-service/rbac.md
 * "Permission Evaluation Algorithm".
 *
 * <p>Returns {@code false} for any of: unknown operator, inactive operator,
 * or missing permission binding. Infrastructure adapter implementations must
 * fail-closed (treat DB errors as deny).
 */
public interface PermissionEvaluator {

    /** @return true if the operator holds {@code permission} via any of its roles. */
    boolean hasPermission(String operatorId, String permission);

    /** @return true if the operator holds every permission in {@code permissions}. */
    boolean hasAllPermissions(String operatorId, Collection<String> permissions);
}
