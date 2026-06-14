package com.example.account.presentation.dto.response;

/**
 * TASK-BE-372 (ADR-MONO-034 U6 step 3b): Response DTO for the
 * GET /internal/tenants/{tenantId}/accounts/{accountId}/identity endpoint.
 *
 * <p>{@code identityId} is the account's central identity (registry from step 3a),
 * or {@code null} when the account does not exist in the tenant OR has no identity
 * yet (enumeration-safe — no 404). The caller (operator-link 3c / provisioning 3d)
 * fail-softs on a null value.
 */
public record AccountIdentityResponse(
        String accountId,
        String tenantId,
        String identityId
) {
    public static AccountIdentityResponse of(String accountId, String tenantId, String identityId) {
        return new AccountIdentityResponse(accountId, tenantId, identityId);
    }
}
