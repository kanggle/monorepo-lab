package com.example.auth.presentation.dto;

/**
 * TASK-MONO-295 (ADR-MONO-040 Phase 2) — response for the internal
 * {@code GET /internal/auth/credentials/{accountId}/email} resolution.
 *
 * <p>Carries the resolved login email (the legacy operator-resolution fallback key
 * for the login-time operator-token exchange). {@code email} is {@code null} when
 * no credential row exists for the {@code accountId} — the admin-service caller
 * then proceeds on account_id-only resolution (graceful). The value is
 * {@code confidential} PII; the caller keeps it off logged URLs (header, not query
 * param) and never logs it.
 */
public record ResolveCredentialEmailResponse(
        String accountId,
        String email
) {
}
