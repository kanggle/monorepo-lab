package com.example.erp.masterdata.domain.authorization;

/**
 * Outcome of an authorization check (erp E6 — fail-CLOSED).
 *
 * <ul>
 *   <li>{@link Outcome#ALLOW} — caller has the required role AND the target
 *       data is within the caller's data-scope.</li>
 *   <li>{@link Outcome#DENY_ROLE} — required role not present →
 *       {@code PERMISSION_DENIED}.</li>
 *   <li>{@link Outcome#DENY_SCOPE} — target data outside caller's data-scope →
 *       {@code DATA_SCOPE_FORBIDDEN}.</li>
 * </ul>
 *
 * <p>Pure Java — no framework imports.
 */
public record AuthorizationDecision(Outcome outcome, String reason) {

    public enum Outcome { ALLOW, DENY_ROLE, DENY_SCOPE }

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(Outcome.ALLOW, null);
    }

    public static AuthorizationDecision denyRole(String reason) {
        return new AuthorizationDecision(Outcome.DENY_ROLE, reason);
    }

    public static AuthorizationDecision denyScope(String reason) {
        return new AuthorizationDecision(Outcome.DENY_SCOPE, reason);
    }

    public boolean allowed() {
        return outcome == Outcome.ALLOW;
    }
}
