package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.domain.po.status.ActorType;

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
        // Accept the assume-tenant operator role (iam's token-exchange mints SCM_OPERATOR —
        // OperatorRoleDerivation) alongside the generic roles a directly-provisioned operator
        // carries. Without SCM_OPERATOR a cross-tenant console operator passes the gateway
        // (RoleAdmissions.roleOrScope admits any role) but is silently downgraded to BUYER
        // here (TASK-MONO-417). Additive, so existing generic operators are unaffected.
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN")
                || hasRole("SCM_OPERATOR");
    }

    /**
     * Map the actor's role set to a {@link ActorType} for state-machine /
     * audit-log purposes. Falls back to {@link ActorType#BUYER} for ordinary
     * authenticated callers.
     */
    public ActorType actorType() {
        if (isOperator()) return ActorType.OPERATOR;
        return ActorType.BUYER;
    }
}
