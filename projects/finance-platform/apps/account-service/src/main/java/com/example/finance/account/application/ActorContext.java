package com.example.finance.account.application;

import com.example.finance.account.domain.account.ActorType;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isOperator() {
        // Accept the assume-tenant operator role (iam's token-exchange mints FINANCE_OPERATOR —
        // OperatorRoleDerivation) alongside the generic roles a directly-provisioned operator
        // carries. Without FINANCE_OPERATOR a cross-tenant operator passes the gateway
        // (RoleAdmissions.roleOrScope admits any role) but is silently downgraded to HOLDER
        // here (TASK-MONO-417). Additive — existing generic operators unaffected.
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN")
                || hasRole("FINANCE_OPERATOR");
    }

    /** Map to a domain {@link ActorType} for audit/history rows (F6). */
    public ActorType actorType() {
        return isOperator() ? ActorType.OPERATOR : ActorType.HOLDER;
    }
}
