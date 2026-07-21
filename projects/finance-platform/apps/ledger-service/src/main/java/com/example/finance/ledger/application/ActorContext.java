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

    /**
     * The audit actor identity — the JWT {@code subject} when present, else the
     * {@code tenantId}. Single-sources the {@code actorIdentity(...)} helper that
     * was duplicated byte-identically across the ledger controllers
     * (TASK-FIN-BE-061 F1).
     */
    public String identity() {
        return subject != null ? subject : tenantId;
    }
}
