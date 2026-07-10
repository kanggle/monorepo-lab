package com.example.admin.application.exception;

/**
 * TASK-BE-492 (ADR-MONO-047 D5) — 403 {@code ORG_NODE_SELF_CEILING_DENIED}.
 *
 * <p>An {@code ORG_ADMIN @ N} may not edit {@code N}'s own ceiling: that ceiling is the
 * bound on its own authority, so editing it is self-escalation. Only a <b>strict
 * ancestor</b> (or {@code SUPER_ADMIN}) may. Exact AWS Organizations parity — an SCP
 * attached to your own OU cannot be detached from within it.
 *
 * <p>403 (not 404) is deliberate here: the actor demonstrably administers the node, so its
 * existence is not a secret. The 404 rule covers nodes <em>outside</em> the actor's reach.
 */
public class OrgNodeSelfCeilingDeniedException extends RuntimeException {
    public OrgNodeSelfCeilingDeniedException(String message) {
        super(message);
    }
}
