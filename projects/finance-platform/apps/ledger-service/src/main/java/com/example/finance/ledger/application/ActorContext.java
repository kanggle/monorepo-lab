package com.example.finance.ledger.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Value object —
 * keeps Spring Security types out of the application layer. The ledger read API
 * is tenant-scoped; {@code tenantId} drives row-level isolation.
 */
public record ActorContext(String subject, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
