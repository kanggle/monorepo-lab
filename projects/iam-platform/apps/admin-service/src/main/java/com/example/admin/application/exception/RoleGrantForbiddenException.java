package com.example.admin.application.exception;

/**
 * ADR-MONO-024 D3 (TASK-BE-347) — thrown when an operator attempts to grant a
 * role it may not: a platform/privileged role ({@code SUPER_ADMIN}), or a role
 * carrying a permission the actor does not itself hold (no granting more than you
 * have). A platform-scope actor (SUPER_ADMIN, {@code operator.manage} @ {@code '*'})
 * is unconstrained and never triggers this.
 *
 * <p>Maps to HTTP 403 {@code ROLE_GRANT_FORBIDDEN} in {@code AdminExceptionHandler}.
 */
public class RoleGrantForbiddenException extends RuntimeException {

    public RoleGrantForbiddenException(String message) {
        super(message);
    }
}
