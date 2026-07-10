package com.example.admin.application.exception;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — 422 {@code ORG_ADMIN_GRANT_OUT_OF_CEILING}.
 *
 * <p>Raised when a node-scoped grant is refused because the node's
 * {@code effectiveCeiling(N)} does not cover it. Two triggers today:
 * <ul>
 *   <li>the ceiling permits nothing ({@code BOUNDED([])}) — a node that entitles no domain
 *       cannot receive an administrator whose authority is bounded by it;</li>
 *   <li>the ceiling could not be resolved, and the fail-closed default ({@code BOUNDED([])})
 *       therefore applies — account-service being down denies the grant rather than
 *       silently falling back to {@code UNBOUNDED}.</li>
 * </ul>
 *
 * <p>Never raised for a {@code SUPER_ADMIN} attempt (that is 403 from {@code RoleGrantGuard})
 * nor for a role the actor does not hold (also 403) — the ceiling bounds <em>entitlement</em>,
 * never IAM role minting (ADR-023 plane separation).
 */
public class OrgAdminGrantOutOfCeilingException extends RuntimeException {
    public OrgAdminGrantOutOfCeilingException(String message) {
        super(message);
    }
}
