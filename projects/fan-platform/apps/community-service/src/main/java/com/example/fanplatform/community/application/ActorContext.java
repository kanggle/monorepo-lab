package com.example.fanplatform.community.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Passed to use
 * cases as a value object — keeps Spring Security types out of the application
 * layer.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isOperator() {
        // Accept the assume-tenant operator role (iam's token-exchange mints FAN_OPERATOR —
        // OperatorRoleDerivation) alongside the generic roles a directly-provisioned operator
        // carries. Without FAN_OPERATOR a cross-tenant operator passes the gateway
        // (RoleAdmissions.roleOrScope admits any role) but is silently treated as a
        // non-operator here (TASK-MONO-417). Additive — existing generic operators unaffected.
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN")
                || hasRole("FAN_OPERATOR");
    }
}
