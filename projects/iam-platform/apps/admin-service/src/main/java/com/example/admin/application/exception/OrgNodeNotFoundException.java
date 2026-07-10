package com.example.admin.application.exception;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — 404 {@code ORG_NODE_NOT_FOUND}.
 *
 * <p>Raised both when the node genuinely does not exist AND when it exists but lies
 * outside the actor's reach. <b>Cross-scope is 404, not 403</b>: a 403 would confirm the
 * existence of a node outside the actor's subtree. Same enumeration-safety convention as
 * the cross-tenant account path (BE-467) and {@code OperatorAdminScopeConfinementIntegrationTest}.
 */
public class OrgNodeNotFoundException extends RuntimeException {
    public OrgNodeNotFoundException(String message) {
        super(message);
    }
}
