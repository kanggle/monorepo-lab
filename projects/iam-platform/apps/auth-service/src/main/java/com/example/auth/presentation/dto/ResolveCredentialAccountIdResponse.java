package com.example.auth.presentation.dto;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — response for the internal
 * {@code POST /internal/auth/credentials/account-id-by-email} resolution.
 *
 * <p>Always HTTP 200 (fail-soft boundary): {@code accountId} is {@code null} when
 * no credential matches the supplied {@code (tenantId, email)} scope (and the email
 * is not globally unambiguous). The admin-service backfill caller then leaves that
 * operator's {@code oidc_subject} unchanged (it stays resolvable via the RETAINED
 * email fallback) — never an error.
 *
 * @param accountId the resolved credential {@code account_id}, or {@code null} when
 *                  unresolved (fail-soft)
 */
public record ResolveCredentialAccountIdResponse(
        String accountId
) {
}
