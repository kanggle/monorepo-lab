package com.example.admin.application.exception;

/**
 * TASK-BE-298 / ADR-MONO-014 — the GAP OIDC subject token presented to
 * {@code POST /api/admin/auth/token-exchange} failed validation, OR its OIDC
 * subject did not resolve to an active {@code admin_operators} row.
 *
 * <p>Extends {@link OperatorUnauthorizedException} deliberately: the task +
 * admin-api.md mandate that BOTH a subject-token validation failure AND a
 * fail-closed no-mapping/deactivated operator surface as the SAME
 * {@code 401 TOKEN_INVALID} envelope (no token minted, no information leak
 * about which branch failed). Reusing the existing
 * {@code OperatorUnauthorizedException} → {@code 401 TOKEN_INVALID} handler
 * (AdminExceptionHandler) is the spec-sanctioned fail-closed behavior — no new
 * exception handler or error code is introduced.
 */
public class SubjectTokenInvalidException extends OperatorUnauthorizedException {
    public SubjectTokenInvalidException(String message) {
        super(message);
    }
}
