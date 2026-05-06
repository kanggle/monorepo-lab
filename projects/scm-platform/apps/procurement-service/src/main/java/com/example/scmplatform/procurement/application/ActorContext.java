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
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN");
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
