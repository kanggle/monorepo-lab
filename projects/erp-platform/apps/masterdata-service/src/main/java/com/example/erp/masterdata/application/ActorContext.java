package com.example.erp.masterdata.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer.
 *
 * <p>Roles are normalized to upper-case strings; common erp roles are
 * {@code ERP_OPERATOR}, {@code ERP_ADMIN}, {@code SUPER_ADMIN}. Scope claims
 * ({@code erp.read} / {@code erp.write}) are extracted by the JWT converter.
 * The {@code dataScopeDepartmentIds} set holds the department ids the actor
 * may read/write under; an empty set + non-wildcard is the fail-closed denial
 * default.
 *
 * <p>{@code "*"} in {@code dataScopeDepartmentIds} = platform-wide scope (used
 * by {@code client_credentials} machine tokens per architecture.md §
 * Authorization matrix point 3).
 */
public record ActorContext(String actorId, String tenantId, Set<String> roles,
                            Set<String> dataScopeDepartmentIds) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasScope(String scope) {
        return roles != null && roles.contains(scope);
    }

    public boolean isPlatformScope() {
        return dataScopeDepartmentIds != null
                && dataScopeDepartmentIds.contains("*");
    }

    public boolean isOperator() {
        return hasRole("ERP_OPERATOR") || hasRole("ERP_ADMIN")
                || hasRole("SUPER_ADMIN");
    }
}
