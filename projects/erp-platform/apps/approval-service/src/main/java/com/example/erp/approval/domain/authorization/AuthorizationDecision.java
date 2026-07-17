package com.example.erp.approval.domain.authorization;

/**
 * Outcome of an authorization check (erp E6 — fail-CLOSED).
 *
 * <ul>
 *   <li>{@link Outcome#ALLOW} — caller has the required role (v1). Subject data-scope
 *       confinement is a v2 concern, so v1 ALLOW means "role satisfied".</li>
 *   <li>{@link Outcome#DENY_ROLE} — required role not present →
 *       {@code PERMISSION_DENIED}.</li>
 *   <li>{@link Outcome#DENY_SCOPE} — target outside caller data-scope →
 *       {@code DATA_SCOPE_FORBIDDEN}. <b>Reserved for the v2 {@code permission-service}</b>
 *       (TASK-ERP-BE-030) — the v1 {@code JwtBackedAuthorizationAdapter} never produces it
 *       (subject owning-department is not resolved in v1); the exception + error-registry
 *       mapping remain so the v2 client can emit it without a contract change.</li>
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
