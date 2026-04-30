package com.example.admin.application;

/**
 * Represents the currently authenticated operator for the in-flight request.
 * Populated by {@code OperatorAuthenticationFilter} from a verified operator JWT
 * ({@code token_type=admin}).
 *
 * <p>Roles/permissions are intentionally NOT carried here: per
 * {@code rbac.md} D5, authorization is resolved via DB lookup on every request
 * so that role revokes take effect immediately. The JWT jti is captured for
 * outbox envelope {@code actor.sessionId} emission (data-model.md envelope
 * mapping); {@code jti} may be {@code null} for tokens predating this change.
 */
public record OperatorContext(
        String operatorId,
        String jti
) {
}
