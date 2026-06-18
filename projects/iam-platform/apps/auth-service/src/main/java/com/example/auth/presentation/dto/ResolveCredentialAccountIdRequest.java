package com.example.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — request body for the internal
 * {@code POST /internal/auth/credentials/account-id-by-email} email→account_id
 * resolution (the reverse of the Phase-2 account_id→email endpoint).
 *
 * <p><b>POST + body (NOT a path/query param) is deliberate</b>: {@code email} is
 * {@code confidential} PII and must NOT land in request URLs / gateway access logs
 * (the Phase-2 "no PII in query logs" discipline). {@code tenantId} scopes the
 * lookup to the operator's tenant because {@code credentials.email} is unique only
 * <b>per tenant</b> ({@code uk_credentials_tenant_email}); see
 * {@link com.example.auth.application.ResolveCredentialAccountIdUseCase}.
 *
 * @param email    the operator's login email (required; normalized server-side)
 * @param tenantId the operator's tenant scope (required; SUPER_ADMIN passes the
 *                 {@code '*'} sentinel — its credential is seeded under {@code '*'})
 */
public record ResolveCredentialAccountIdRequest(
        @NotBlank String email,
        @NotBlank String tenantId
) {
}
