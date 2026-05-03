package com.example.fanplatform.artist.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Passed to use
 * cases as a value object — keeps Spring Security types out of the application
 * layer (Hexagonal port purity).
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /** True when the actor has any of the admin-tier roles. */
    public boolean isAdmin() {
        return hasRole("ADMIN") || hasRole("SUPER_ADMIN") || hasRole("OPERATOR");
    }
}
