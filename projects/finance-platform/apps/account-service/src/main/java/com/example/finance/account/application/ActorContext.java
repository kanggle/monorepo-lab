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
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN");
    }

    /** Map to a domain {@link ActorType} for audit/history rows (F6). */
    public ActorType actorType() {
        return isOperator() ? ActorType.OPERATOR : ActorType.HOLDER;
    }
}
