package com.example.admin.application.exception;

/**
 * TASK-BE-520 / ADR-MONO-046 D4 — 422 {@code GROUP_GRANT_NO_ESCALATION}: a
 * TENANT_ASSIGNMENT grant targets a tenant outside the actor's {@code operator.manage}
 * effective admin scope. Checked at grant time AND at add-member fan-out time so a group
 * can never be a bypass for granting a tenant the actor does not itself administer. (The
 * ROLE no-escalation path uses the reused {@code RoleGrantGuard} → 403
 * {@code ROLE_GRANT_FORBIDDEN}.)
 */
public class GroupGrantNoEscalationException extends RuntimeException {
    public GroupGrantNoEscalationException(String message) {
        super(message);
    }
}
